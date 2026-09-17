package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.enums.VerificationStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SellerVerificationService {

    SellerVerificationResponse submitVerification(UUID userId, SellerVerificationRequest request);

    SellerVerificationResponse getCurrentVerification(UUID userId);

    PageResponse<SellerVerificationResponse> getAdminVerifications(VerificationStatus status, Pageable pageable);

    SellerVerificationResponse getAdminVerificationById(UUID verificationId);

    SellerVerificationResponse approveVerification(UUID adminId, UUID verificationId);

    SellerVerificationResponse rejectVerification(UUID adminId, UUID verificationId, SellerVerificationReviewRequest request);
}
