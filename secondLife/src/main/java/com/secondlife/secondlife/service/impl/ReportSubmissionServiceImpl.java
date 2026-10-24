package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateReportSubmissionRequest;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.dto.response.ReportSubmissionResponse;
import com.secondlife.secondlife.entity.ReportSubmission;
import com.secondlife.secondlife.entity.UserReport;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.ReportSubmissionMapper;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.ReportSubmissionRepository;
import com.secondlife.secondlife.repository.UserReportRepository;
import com.secondlife.secondlife.service.ReportEvidenceService;
import com.secondlife.secondlife.service.ReportSubmissionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReportSubmissionServiceImpl implements ReportSubmissionService {
    private final UserReportRepository reports;
    private final OrderRepository orders;
    private final ReportSubmissionRepository submissions;
    private final ReportEvidenceService evidence;
    private final ReportSubmissionMapper mapper;
    private final ObjectMapper json;
    private final Clock clock;

    public ReportSubmissionServiceImpl(UserReportRepository reports, OrderRepository orders,
            ReportSubmissionRepository submissions, ReportEvidenceService evidence,
            ReportSubmissionMapper mapper, ObjectMapper json, @Qualifier("orderReleaseClock") Clock clock) {
        this.reports = reports;
        this.orders = orders;
        this.submissions = submissions;
        this.evidence = evidence;
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReportSubmissionResponse create(UUID actorId, UUID reportId, CreateReportSubmissionRequest request) {
        if (request == null || request.requestId() == null) throw new BadRequestException("requestId is required");
        var initial = findReport(reportId, false);
        requireParticipant(initial, actorId);
        requireOrderReport(initial);
        UUID orderId = initial.getOrderId();

        // Resolution and settlement also lock order before report. The report lock serializes retries.
        orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        var report = findReport(reportId, true);
        requireParticipant(report, actorId);
        requireOrderReport(report);
        if (!Objects.equals(orderId, report.getOrderId())) {
            throw new ConflictException("The report order changed; retry the submission");
        }

        var replay = submissions.findByReportIdAndAuthorIdAndRequestId(reportId, actorId, request.requestId());
        if (replay.isPresent()) {
            var previous = mapper.toResponse(replay.get(), json);
            if (!Objects.equals(previous.description(), request.description())
                    || !previous.evidenceUrls().equals(request.evidenceUrls())) {
                throw new ConflictException("requestId was already used for different response details");
            }
            return previous;
        }
        if (report.getStatus() != Status.OPEN && report.getStatus() != Status.IN_REVIEW) {
            throw new ConflictException("Responses can only be added to an open report or a report under review");
        }
        validateRequest(request);
        evidence.validateOwner(actorId, request.evidenceUrls());
        evidence.bind(actorId, reportId, request.evidenceUrls());
        var submission = new ReportSubmission(UUID.randomUUID(), reportId, actorId,
                report.getReporterId().equals(actorId) ? "BUYER" : "SELLER", request.requestId(),
                request.description(), json.writeValueAsString(request.evidenceUrls()),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        return mapper.toResponse(submissions.saveAndFlush(submission), json);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportSubmissionResponse> list(UUID actorId, boolean reviewer, UUID reportId, Pageable pageable) {
        var report = findReport(reportId, false);
        if (!reviewer) requireParticipant(report, actorId);
        requireOrderReport(report);
        var page = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100));
        var result = reviewer
                ? submissions.findByReportIdOrderByCreatedAtAscIdAsc(reportId, page)
                : submissions.findByReportIdAndAuthorIdOrderByCreatedAtAscIdAsc(reportId, actorId, page);
        return PageResponse.from(result.map(submission -> mapper.toResponse(submission, json)));
    }

    private UserReport findReport(UUID reportId, boolean lock) {
        return reports.findById(reportId, lock).orElseThrow(() -> new NotFoundException("Report not found"));
    }

    private void requireParticipant(UserReport report, UUID actorId) {
        if (!report.getReporterId().equals(actorId) && !report.getSellerId().equals(actorId)) {
            throw new ForbiddenException("Only the reporter or seller can access their own report responses");
        }
    }

    private void requireOrderReport(UserReport report) {
        if (report.getOrderId() == null) throw new BadRequestException("Responses are only supported for order reports");
    }

    private void validateRequest(CreateReportSubmissionRequest request) {
        if (request.description() == null || request.description().isBlank() || request.description().length() > 2000) {
            throw new BadRequestException("Response description is required and must not exceed 2000 characters");
        }
        List<String> urls = request.evidenceUrls();
        if (urls.size() > 10) throw new BadRequestException("A response may contain at most 10 evidence items");
        if (urls.stream().anyMatch(url -> url == null || url.isBlank() || url.length() > 2048)) {
            throw new BadRequestException("Evidence references must be nonblank and must not exceed 2048 characters");
        }
        if (new HashSet<>(urls).size() != urls.size()) throw new BadRequestException("Evidence references must be distinct");
    }
}

