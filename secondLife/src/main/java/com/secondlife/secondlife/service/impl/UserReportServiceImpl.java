package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.config.ReputationProperties;
import com.secondlife.secondlife.dto.request.ReportRequests.Create;
import com.secondlife.secondlife.dto.request.ReportRequests.Decision;
import com.secondlife.secondlife.dto.request.ReportRequests.ListingCreate;
import com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.Resolve;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.dto.request.ReportRequests.TargetType;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.dto.response.SellerOrderDisputeResponse;
import com.secondlife.secondlife.dto.response.SellerOrderDisputeDetailResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.UserReportMapper;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserReportRepository;
import com.secondlife.secondlife.service.ListingReportNotifications;
import com.secondlife.secondlife.service.OrderDisputeService;
import com.secondlife.secondlife.service.ReportEvidenceService;
import com.secondlife.secondlife.service.SellerReputationService;
import com.secondlife.secondlife.service.UserReportService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service("userReportService")
public class UserReportServiceImpl implements UserReportService {
    private static final Duration SELLER_RESPONSE_WINDOW = Duration.ofHours(24);
    private final Clock clock;
    private final OrderDisputeService disputes;
    private final UserReportRepository reports;
    private final OrderRepository orders;
    private final PostRepository posts;
    private final SellerReputationService reputation;
    private final ReputationProperties properties;
    private final ReportEvidenceService evidence;
    private final ListingReportNotifications notifications;
    private final UserReportMapper mapper;
    private final ObjectMapper json;
    public UserReportServiceImpl(UserReportRepository reports,OrderRepository orders,PostRepository posts,
            SellerReputationService reputation,ReputationProperties properties,
            ReportEvidenceService evidence,ListingReportNotifications notifications,UserReportMapper mapper,ObjectMapper json,OrderDisputeService disputes,
            @Qualifier("orderReleaseClock") Clock clock) {
        this.clock = clock;
        this.disputes=disputes;
        this.reports=reports; this.orders=orders; this.posts=posts; this.reputation=reputation;
        this.properties=properties; this.evidence=evidence; this.notifications=notifications;
        this.mapper=mapper; this.json=json;
    }

    @Transactional
    public ReportResponse createListing(UUID reporterId,UUID postId,ListingCreate request) {
        return create(reporterId,new Create(request.requestId(),postId,null,ReasonCode.valueOf(request.reasonCode().name()),
                request.description(),request.evidenceUrls(),request.relatedPostId()));
    }

    @Transactional
    public ReportResponse create(UUID reporterId,Create request) {
        // Serialize request retries by reporter without acquiring a user/order row lock in reverse order.
        reports.lockReporter(reporterId);
        var replay = reports.findByRequest(reporterId,request.requestId());
        if (!replay.isEmpty()) {
            var previous=mapper.toResponse(replay.getFirst(),json);
            if (!Objects.equals(previous.orderId(),request.orderId())
                    || (request.postId()!=null && !previous.postId().equals(request.postId()))
                    || previous.reasonCode()!=request.reasonCode() || !previous.description().equals(request.description().trim())
                    || !previous.evidenceUrls().equals(request.evidenceUrls())
                    || !Objects.equals(previous.relatedPostId(),request.relatedPostId()))
                throw new ConflictException("requestId was already used for different report details");
            return previous;
        }
        validateEvidence(reporterId,request.evidenceUrls());
        UUID postId,sellerId;
        Instant acceptedAt=null;
        if ((request.postId()==null)==(request.orderId()==null)) throw new BadRequestException("Provide exactly one of postId or orderId");
        if (request.orderId()!=null) {
            if (request.reasonCode()==ReasonCode.DUPLICATE_LISTING || request.relatedPostId()!=null)
                throw new BadRequestException("Duplicate listing reports and relatedPostId require a postId, not an orderId");
            Order order=orders.findByIdForUpdate(request.orderId()).orElseThrow(() -> new NotFoundException("Order not found"));
            if (!order.getBuyer().getId().equals(reporterId)) throw new ForbiddenException("Only the buyer of this order can report it");
            if (request.evidenceUrls().isEmpty()) {
                throw new BadRequestException("Vui lòng đính kèm ít nhất 1 ảnh hoặc video bằng chứng để gửi khiếu nại.");
            }
            acceptedAt=disputes.open(order);
            postId=order.getPost().getId(); sellerId=order.getSeller().getId();
        } else {
            var post=posts.findById(request.postId()).orElseThrow(() -> new NotFoundException("Post not found"));
            if (!Set.of("ACTIVE","RESERVED","SOLD").contains(post.getStatus())) throw new ConflictException("Only a published listing can be reported");
            if (!Set.of(ReasonCode.FRAUD,ReasonCode.MISLEADING_DESCRIPTION,ReasonCode.DUPLICATE_LISTING,ReasonCode.OTHER).contains(request.reasonCode()))
                throw new BadRequestException("Listing reports support FRAUD, MISLEADING_DESCRIPTION, DUPLICATE_LISTING and OTHER only");
            postId=post.getId(); sellerId=post.getUser().getId();
        }
        validateRelatedListing(request,postId);
        if (sellerId.equals(reporterId)) throw new BadRequestException("You cannot report your own listing or order");
        boolean pending=reports.hasPending(reporterId,postId,request.orderId(),request.reasonCode());
        if (pending) throw new ConflictException("A report for this target and reason is already awaiting review");
        UUID id=UUID.randomUUID();
        reports.insert(id,reporterId,sellerId,postId,request,json.writeValueAsString(request.evidenceUrls()),acceptedAt);
        evidence.bind(reporterId,id,request.evidenceUrls());
        var report=find(id,false);
        notifications.created(report);
        return report;
    }

    @Transactional(readOnly=true)
    public ReportResponse get(UUID actorId,boolean reviewer,UUID id) {
        var report=find(id,false);
        if (!reviewer && !report.reporterId().equals(actorId)) throw new ForbiddenException("You cannot read this report or its private evidence");
        return report;
    }
    @Transactional(readOnly=true)
    public PageResponse<ReportResponse> mine(UUID reporterId,TargetType targetType,Pageable page) {
        return PageResponse.from(reports.mine(reporterId,targetType==null?null:targetType.name(),
                PageRequest.of(page.getPageNumber(),page.getPageSize())).map(report -> mapper.toResponse(report,json)));
    }
    @Transactional(readOnly=true)
    public PageResponse<SellerOrderDisputeResponse> sellerDisputes(UUID sellerId,Pageable page) {
        return PageResponse.from(reports.sellerDisputes(sellerId,PageRequest.of(page.getPageNumber(),page.getPageSize())));
    }
    @Transactional(readOnly=true)
    public PageResponse<ReportResponse> queue(Status status,TargetType targetType,Pageable page) {
        return PageResponse.from(reports.queue(status==null?null:status.name(),targetType==null?null:targetType.name(),
                PageRequest.of(page.getPageNumber(),page.getPageSize())).map(report -> mapper.toResponse(report,json)));
    }
    @Override
    @Transactional(readOnly = true)
    public SellerOrderDisputeDetailResponse sellerDisputeDetail(UUID sellerId, UUID id) {
        var report = find(id, false);
        if (report.orderId() == null || !sellerId.equals(report.sellerId())) {
            throw new NotFoundException("Order dispute not found");
        }
        return new SellerOrderDisputeDetailResponse(
                report.id(), report.reasonCode(), report.description(), report.evidenceUrls(),
                report.status(), report.responsibleParty(), report.decisionReason(), report.resolvedAt(),
                report.orderId(), report.postId(), report.resolutionAction(), report.refundedAt(),
                report.refundAmount(), report.returnShipmentId(), report.sellerResponseDeadlineAt(),
                report.sellerRespondedAt(), report.createdAt()
        );
    }

    @Override
    @Transactional
    public ReportResponse requestSellerResponse(UUID reviewerId, UUID id) {
        var report = reports.findById(id, true).orElseThrow(() -> new NotFoundException("Report not found"));
        if (report.getOrderId() == null) throw new BadRequestException("Only order disputes support seller response requests");
        if (reviewerId.equals(report.getSellerId()) || reviewerId.equals(report.getReporterId())) {
            throw new ForbiddenException("You cannot review a report concerning yourself");
        }
        if (report.getStatus() != Status.OPEN && report.getStatus() != Status.IN_REVIEW) {
            throw new ConflictException("This order dispute has already been resolved");
        }
        if (report.getStatus() == Status.IN_REVIEW && !reviewerId.equals(report.getReviewerId())) {
            throw new ForbiddenException("Only the assigned reviewer can request a response for this report");
        }
        if (report.getSellerResponseDeadlineAt() != null) return mapper.toResponse(report, json);
        if (report.getSellerRespondedAt() != null) throw new ConflictException("The seller has already responded");

        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        report.setSellerResponseDeadlineAt(now.plus(SELLER_RESPONSE_WINDOW));
        report.setSellerResponseRequestedBy(reviewerId);
        report.setUpdatedAt(now);
        var response = mapper.toResponse(reports.saveAndFlush(report), json);
        notifications.sellerResponseRequested(response);
        return response;
    }

    @Transactional
    public ReportResponse startReview(UUID reviewerId,UUID id) {
        var report=find(id,true);
        if (report.status()==Status.IN_REVIEW && reviewerId.equals(report.reviewerId())) {
            sellerMissedResponseDeadline(report);
            return report;
        }
        if (report.status()!=Status.OPEN) throw new ConflictException("Report is already assigned or resolved");
        if (reviewerId.equals(report.sellerId()) || reviewerId.equals(report.reporterId()))
            throw new ForbiddenException("You cannot review a report concerning yourself");
        sellerMissedResponseDeadline(report);
        reports.assign(id,reviewerId);
        var updated=find(id,false);
        notifications.reviewStarted(updated);
        return updated;
    }
    @Transactional
    public ReportResponse resolve(UUID reviewerId,UUID id,Resolve request) {
        // Same order -> report -> seller reputation lock order as settlement. Avoid holding a report
        // lock while waiting for an order that another resolution/settlement may already hold.
        var initial=find(id,false);
        if (initial.orderId()!=null) orders.findByIdForUpdate(initial.orderId()).orElseThrow(() -> new NotFoundException("Order not found"));
        var report=find(id,true);
        if (!reviewerId.equals(report.reviewerId())) throw new ForbiddenException("Only the assigned reviewer can resolve this report");
        if (report.status()==Status.CONFIRMED || report.status()==Status.DISMISSED) {
            ResponsibleParty requestedParty = request.responsibleParty();
            if (requestedParty == null && (report.orderId() != null || request.decision() == Decision.DISMISSED)) {
                requestedParty = report.responsibleParty();
            }
            if (report.status().name().equals(request.decision().name()) && report.responsibleParty()==requestedParty
                    && request.reason().trim().equals(report.decisionReason())
                    && request.resolutionAction()==report.resolutionAction()) return report;
            throw new ConflictException("A resolved report cannot be changed by retrying resolution");
        }
        if (report.status()!=Status.IN_REVIEW) throw new ConflictException("Start review before resolving this report");
        if (reviewerId.equals(report.sellerId()) || reviewerId.equals(report.reporterId())) throw new ForbiddenException("You cannot resolve a report concerning yourself");
        ResponsibleParty party = resolveResponsibleParty(report, request);
        disputes.resolve(report, request, party);
        var updated = find(id, false);
        if (party == ResponsibleParty.SELLER && request.resolutionAction() != ResolutionAction.RETURN_AND_REFUND) {
            reputation.confirmSellerFault(updated, reviewerId, request.reason().trim());
        }
        notifications.resolved(updated);
        return updated;
    }
    private ResponsibleParty resolveResponsibleParty(ReportResponse report, Resolve request) {
        boolean sellerMissedDeadline = sellerMissedResponseDeadline(report);
        ResponsibleParty requested = request.responsibleParty();

        if (request.decision() == Decision.DISMISSED) {
            ResponsibleParty expected = sellerMissedDeadline ? ResponsibleParty.SELLER : ResponsibleParty.NONE;
            if (requested != null && requested != expected) {
                if (sellerMissedDeadline) {
                    throw new ConflictException("The seller missed the response deadline; confirm seller responsibility");
                }
                throw new BadRequestException("A dismissed report must use responsibleParty NONE");
            }
            return expected;
        }

        if (report.orderId() == null) {
            if (requested == null || requested == ResponsibleParty.NONE) {
                throw new BadRequestException("A confirmed listing report requires a responsible party");
            }
            return requested;
        }

        ResponsibleParty expected = request.resolutionAction() == ResolutionAction.RETURN_AND_REFUND || sellerMissedDeadline
                ? ResponsibleParty.SELLER : ResponsibleParty.NONE;
        if (requested != null && requested != expected) {
            if (sellerMissedDeadline) {
                throw new ConflictException("The seller missed the response deadline; confirm seller responsibility");
            }
            throw new BadRequestException("Order dispute responsibility is determined by the resolution action");
        }
        return expected;
    }

    /** Returns whether the reviewer must record seller responsibility after the response window expires. */
    private boolean sellerMissedResponseDeadline(ReportResponse report) {
        if (report.orderId() == null) return false;
        Instant deadline = report.sellerResponseDeadlineAt();
        Instant respondedAt = report.sellerRespondedAt();
        if (respondedAt != null && (deadline == null || respondedAt.isBefore(deadline))) return false;
        if (deadline == null || clock.instant().isBefore(deadline)) {
            throw new ConflictException("Wait for the seller response or the 24-hour response deadline before reviewing this order dispute");
        }
        return true;
    }

    private void validateRelatedListing(Create request,UUID postId) {
        if (request.relatedPostId()==null) return;
        if (request.orderId()!=null || request.reasonCode()!=ReasonCode.DUPLICATE_LISTING)
            throw new BadRequestException("relatedPostId is only allowed for duplicate listing reports");
        if (request.relatedPostId().equals(postId)) throw new BadRequestException("Related listing must differ from the reported listing");
        var related=posts.findById(request.relatedPostId()).orElseThrow(() -> new NotFoundException("Related listing not found"));
        if (!Set.of("ACTIVE","RESERVED","SOLD").contains(related.getStatus()))
            throw new ConflictException("Related listing must be published");
    }
    private ReportResponse find(UUID id,boolean lock) {
        return reports.findById(id,lock).map(report -> mapper.toResponse(report,json))
                .orElseThrow(()->new NotFoundException("Report not found"));
    }
    private void validateEvidence(UUID ownerId,List<String> urls) {
        int limit = Math.min(properties.maxEvidenceImages(), ReportEvidenceService.MAX_ATTACHMENTS_PER_SUBMISSION);
        if (urls.size() > limit) throw new BadRequestException("Có thể đính kèm tối đa " + limit + " tệp bằng chứng.");
        if(new HashSet<>(urls).size()!=urls.size()) throw new BadRequestException("Report evidence images must be distinct");
        evidence.validateOwner(ownerId,urls);
    }
}
