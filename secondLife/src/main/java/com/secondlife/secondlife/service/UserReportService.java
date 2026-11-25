package com.secondlife.secondlife.service;

import org.springframework.data.domain.Pageable;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.dto.response.SellerOrderDisputeResponse;
import com.secondlife.secondlife.dto.response.SellerOrderDisputeDetailResponse;
import java.util.UUID;
import static com.secondlife.secondlife.dto.request.ReportRequests.*;

public interface UserReportService {
    ReportResponse createListing(UUID reporterId, UUID postId, ListingCreate request);

    ReportResponse create(UUID reporterId, Create request);

    ReportResponse get(UUID actorId, boolean reviewer, UUID id);

    PageResponse<ReportResponse> mine(UUID reporterId, TargetType targetType, Pageable page);

    PageResponse<SellerOrderDisputeResponse> sellerDisputes(UUID sellerId, Pageable page);

    PageResponse<ReportResponse> queue(Status status, TargetType targetType, Pageable page);

    SellerOrderDisputeDetailResponse sellerDisputeDetail(UUID sellerId, UUID id);

    ReportResponse requestSellerResponse(UUID reviewerId, UUID id);

    ReportResponse startReview(UUID reviewerId, UUID id);

    ReportResponse resolve(UUID reviewerId, UUID id, Resolve request);

}
