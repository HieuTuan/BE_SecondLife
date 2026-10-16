package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.config.ReputationProperties;
import com.secondlife.secondlife.dto.reputation.*;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static com.secondlife.secondlife.dto.reputation.ReportRequests.*;

@Service
public class UserReportService {
    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final PostRepository posts;
    private final SellerReputationService reputation;
    private final ReputationProperties properties;
    private final ObjectMapper mapper;
    private final ReportEvidenceService evidence;
    private final ListingReportNotifications notifications;
    public UserReportService(JdbcTemplate jdbc,OrderRepository orders,PostRepository posts,
            SellerReputationService reputation,ReputationProperties properties,ObjectMapper mapper,
            ReportEvidenceService evidence,ListingReportNotifications notifications) {
        this.jdbc=jdbc; this.orders=orders; this.posts=posts; this.reputation=reputation;
        this.properties=properties; this.mapper=mapper; this.evidence=evidence; this.notifications=notifications;
    }

    @Transactional
    public ReportResponse createListing(UUID reporterId,UUID postId,ListingCreate request) {
        return create(reporterId,new Create(request.requestId(),postId,null,ReasonCode.valueOf(request.reasonCode().name()),
                request.description(),request.evidenceUrls(),request.relatedPostId()));
    }

    @Transactional
    public ReportResponse create(UUID reporterId,Create request) {
        validateEvidence(reporterId,request.evidenceUrls());
        // Serialize request retries by reporter without acquiring a user/order row lock in reverse order.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", rs -> { }, "report-create:"+reporterId);
        var replay = jdbc.query("SELECT * FROM user_reports WHERE reporter_id=? AND request_id=?",this::row,reporterId,request.requestId());
        if (!replay.isEmpty()) {
            var previous=replay.getFirst();
            if (!Objects.equals(previous.orderId(),request.orderId())
                    || (request.postId()!=null && !previous.postId().equals(request.postId()))
                    || previous.reasonCode()!=request.reasonCode() || !previous.description().equals(request.description().trim())
                    || !previous.evidenceUrls().equals(request.evidenceUrls())
                    || !Objects.equals(previous.relatedPostId(),request.relatedPostId()))
                throw new ConflictException("requestId was already used for different report details");
            return previous;
        }
        UUID postId,sellerId;
        if ((request.postId()==null)==(request.orderId()==null)) throw new BadRequestException("Provide exactly one of postId or orderId");
        if (request.orderId()!=null) {
            if (request.reasonCode()==ReasonCode.DUPLICATE_LISTING || request.relatedPostId()!=null)
                throw new BadRequestException("Duplicate listing reports and relatedPostId require a postId, not an orderId");
            Order order=orders.findById(request.orderId()).orElseThrow(() -> new NotFoundException("Order not found"));
            if (!order.getBuyer().getId().equals(reporterId)) throw new ForbiddenException("Only the buyer of this order can report it");
            if (order.getStatus()==com.secondlife.secondlife.enums.OrderStatus.PENDING_PAYMENT)
                throw new ConflictException("An unpaid order cannot be reported as a purchase");
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
        boolean pending=Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM user_reports WHERE reporter_id=? AND post_id=?
                AND order_id IS NOT DISTINCT FROM CAST(? AS UUID) AND reason_code=? AND status IN ('OPEN','IN_REVIEW'))
            """,Boolean.class,reporterId,postId,request.orderId(),request.reasonCode().name()));
        if (pending) throw new ConflictException("A report for this target and reason is already awaiting review");
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO user_reports(id,reporter_id,request_id,seller_id,post_id,order_id,reason_code,description,evidence_urls,related_post_id,status,created_at,updated_at)
            VALUES (?,?,?,?,?,?,?,?,CAST(? AS JSONB),?,'OPEN',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """,id,reporterId,request.requestId(),sellerId,postId,request.orderId(),request.reasonCode().name(),request.description().trim(),mapper.writeValueAsString(request.evidenceUrls()),request.relatedPostId());
        evidence.bind(reporterId,id,request.evidenceUrls());
        var report=find(id,false);
        if (report.orderId()==null) notifications.created(report);
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
        return page("reporter_id=?"+targetFilter(targetType),List.of(reporterId),page);
    }
    @Transactional(readOnly=true)
    public PageResponse<ReportResponse> queue(Status status,TargetType targetType,Pageable page) {
        return page((status==null?"TRUE":"status=?")+targetFilter(targetType),status==null?List.of():List.of(status.name()),page);
    }
    @Transactional
    public ReportResponse startReview(UUID reviewerId,UUID id) {
        var report=find(id,true);
        if (report.status()==Status.IN_REVIEW && reviewerId.equals(report.reviewerId())) return report;
        if (report.status()!=Status.OPEN) throw new ConflictException("Report is already assigned or resolved");
        if (reviewerId.equals(report.sellerId()) || reviewerId.equals(report.reporterId()))
            throw new ForbiddenException("You cannot review a report concerning yourself");
        jdbc.update("UPDATE user_reports SET status='IN_REVIEW',reviewer_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",reviewerId,id);
        var updated=find(id,false);
        if (updated.orderId()==null) notifications.reviewStarted(updated);
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
        ResponsibleParty party=request.responsibleParty();
        if (request.decision()==Decision.DISMISSED) {
            if (party!=null && party!=ResponsibleParty.NONE) throw new BadRequestException("A dismissed report must use responsibleParty NONE");
            party=ResponsibleParty.NONE;
        } else if (party==null || party==ResponsibleParty.NONE) throw new BadRequestException("A confirmed report requires responsibleParty SELLER, BUYER or CARRIER");
        if (report.status()==Status.CONFIRMED || report.status()==Status.DISMISSED) {
            if (report.status().name().equals(request.decision().name()) && report.responsibleParty()==party
                    && request.reason().trim().equals(report.decisionReason())) return report;
            throw new ConflictException("A resolved report cannot be changed by retrying resolution");
        }
        if (report.status()!=Status.IN_REVIEW) throw new ConflictException("Start review before resolving this report");
        if (reviewerId.equals(report.sellerId()) || reviewerId.equals(report.reporterId())) throw new ForbiddenException("You cannot resolve a report concerning yourself");
        jdbc.update("""
            UPDATE user_reports SET status=?,responsible_party=?,decision_reason=?,resolved_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?
            """,request.decision().name(),party.name(),request.reason().trim(),id);
        if (request.decision()==Decision.CONFIRMED && party==ResponsibleParty.SELLER)
            reputation.confirmSellerFault(report,reviewerId,request.reason().trim());
        var updated=find(id,false);
        if (updated.orderId()==null) notifications.resolved(updated);
        return updated;
    }
    private String targetFilter(TargetType targetType) {
        return targetType==null ? "" : targetType==TargetType.LISTING ? " AND order_id IS NULL" : " AND order_id IS NOT NULL";
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
        return jdbc.query("SELECT * FROM user_reports WHERE id=?"+(lock?" FOR UPDATE":""),this::row,id)
                .stream().findFirst().orElseThrow(() -> new NotFoundException("Report not found"));
    }
    private PageResponse<ReportResponse> page(String where,List<Object> params,Pageable page) {
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM user_reports WHERE "+where,Long.class,params.toArray());
        var args=new ArrayList<>(params); args.add(page.getPageSize()); args.add(page.getOffset());
        var rows=jdbc.query("SELECT * FROM user_reports WHERE "+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",this::row,args.toArray());
        return PageResponse.from(new PageImpl<>(rows,page,count));
    }
    private ReportResponse row(ResultSet rs,int rowNumber) throws SQLException {
        String party=rs.getString("responsible_party");
        return new ReportResponse(rs.getObject("id",UUID.class),rs.getObject("request_id",UUID.class),rs.getObject("reporter_id",UUID.class),
                rs.getObject("seller_id",UUID.class),rs.getObject("post_id",UUID.class),rs.getObject("order_id",UUID.class),
                ReasonCode.valueOf(rs.getString("reason_code")),rs.getString("description"),
                List.of(mapper.readValue(rs.getString("evidence_urls"),String[].class)),Status.valueOf(rs.getString("status")),
                rs.getObject("reviewer_id",UUID.class),party==null?null:ResponsibleParty.valueOf(party),rs.getString("decision_reason"),
                instant(rs,"created_at"),instant(rs,"updated_at"),instant(rs,"resolved_at"),
                rs.getObject("related_post_id",UUID.class),rs.getObject("order_id")==null?TargetType.LISTING:TargetType.ORDER);
    }
    private void validateEvidence(UUID ownerId,List<String> urls) {
        if(urls.size()>properties.maxEvidenceImages()) throw new BadRequestException("Too many report evidence images; maximum is "+properties.maxEvidenceImages());
        if(new HashSet<>(urls).size()!=urls.size()) throw new BadRequestException("Report evidence images must be distinct");
        evidence.validateOwner(ownerId,urls);
    }
    private static Instant instant(ResultSet rs,String column) throws SQLException {
        var timestamp=rs.getTimestamp(column); return timestamp==null?null:timestamp.toInstant();
    }
}
