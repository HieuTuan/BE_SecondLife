package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateReportSubmissionRequest;
import com.secondlife.secondlife.dto.response.ReportSubmissionResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReportSubmissionService {
    ReportSubmissionResponse create(UUID actorId, UUID reportId, CreateReportSubmissionRequest request);

    PageResponse<ReportSubmissionResponse> list(UUID actorId, boolean reviewer, UUID reportId, Pageable pageable);
}
