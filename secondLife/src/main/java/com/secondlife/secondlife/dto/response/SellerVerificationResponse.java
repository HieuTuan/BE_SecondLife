package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.ReviewSource;
import com.secondlife.secondlife.enums.RiskStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.enums.VerificationType;

import java.time.Instant;
import java.util.UUID;

public record SellerVerificationResponse(
        UUID id,
        UUID userId,
        String userEmail,
        String userFullName,
        VerificationType verificationType,
        String documentNumber,
        String documentFrontUrl,
        String documentBackUrl,
        String selfieUrl,
        SellerVerificationStatus status,
        EkycStatus ekycStatus,
        RiskStatus riskStatus,
        ReviewSource reviewSource,
        ReasonCode reasonCode,
        int resubmissionCount,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        String rejectionReason
) {
    public static SellerVerificationResponse from(SellerVerification sv) {
        String email = sv.getUser() != null ? sv.getUser().getEmail() : null;
        String fullName = (sv.getUser() != null && sv.getUser().getProfile() != null)
                ? sv.getUser().getProfile().getFullName() : null;
        UUID reviewerId = sv.getReviewedBy() != null ? sv.getReviewedBy().getId() : null;

        return new SellerVerificationResponse(
                sv.getId(),
                sv.getUser() != null ? sv.getUser().getId() : null,
                email,
                fullName,
                sv.getVerificationType(),
                sv.getDocumentNumberMasked() != null ? sv.getDocumentNumberMasked() : sv.getDocumentNumber(),
                sv.getDocumentFrontUrl(),
                sv.getDocumentBackUrl(),
                sv.getSelfieUrl(),
                sv.getStatus(),
                sv.getEkycStatus(),
                sv.getRiskStatus(),
                sv.getReviewSource(),
                sv.getReasonCode(),
                sv.getResubmissionCount(),
                sv.getSubmittedAt(),
                sv.getReviewedAt(),
                reviewerId,
                sv.getRejectionReason()
        );
    }
}
