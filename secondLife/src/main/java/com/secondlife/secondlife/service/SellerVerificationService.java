package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationResubmitRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.AdminSellerVerificationDetailResponse;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.RiskStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SellerVerificationService {

    SellerVerificationResponse submitVerification(UUID userId, SellerVerificationRequest request);

    SellerVerificationResponse getCurrentVerification(UUID userId);

    SellerVerificationResponse resubmitVerification(UUID userId, UUID verificationId, SellerVerificationResubmitRequest request);

    PageResponse<SellerVerificationResponse> getAdminVerifications(
            SellerVerificationStatus status,
            EkycStatus ekycStatus,
            RiskStatus riskStatus,
            ReasonCode reasonCode,
            Pageable pageable);

    PageResponse<SellerVerificationResponse> getAdminVerifications(
            SellerVerificationStatus status,
            Pageable pageable);

    AdminSellerVerificationDetailResponse getAdminVerificationById(UUID verificationId);

    SellerVerificationResponse approveVerification(UUID adminId, UUID verificationId);

    SellerVerificationResponse rejectVerification(UUID adminId, UUID verificationId, SellerVerificationReviewRequest request);
}
