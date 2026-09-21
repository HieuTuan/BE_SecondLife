package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.ReviewSource;
import com.secondlife.secondlife.enums.RiskStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.enums.VerificationType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSellerVerificationDetailResponse(
        UUID id,
        UUID userId,
        String userEmail,
        String userFullName,
        VerificationType verificationType,
        String documentNumberMasked,
        String documentFrontUrl,
        String documentBackUrl,
        String selfieUrl,
        SellerVerificationStatus status,
        EkycStatus ekycStatus,
        RiskStatus riskStatus,
        ReviewSource reviewSource,
        ReasonCode reasonCode,
        String providerName,
        String providerReferenceId,
        Double faceMatchScore,
        Double livenessScore,
        Double documentScore,
        Double riskScore,
        int resubmissionCount,
        Instant submittedAt,
        Instant ekycCompletedAt,
        Instant riskEvaluatedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        String rejectionReason,
        List<VerificationEventResponse> eventHistory
) {
    public static AdminSellerVerificationDetailResponse from(
            SellerVerification sv,
            List<VerificationEventResponse> eventHistory) {
        String email = sv.getUser() != null ? sv.getUser().getEmail() : null;
        String fullName = (sv.getUser() != null && sv.getUser().getProfile() != null)
                ? sv.getUser().getProfile().getFullName() : null;
        UUID reviewerId = sv.getReviewedBy() != null ? sv.getReviewedBy().getId() : null;

        return new AdminSellerVerificationDetailResponse(
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
                sv.getProviderName(),
                sv.getProviderReferenceId(),
                sv.getFaceMatchScore(),
                sv.getLivenessScore(),
                sv.getDocumentScore(),
                sv.getRiskScore(),
                sv.getResubmissionCount(),
                sv.getSubmittedAt(),
                sv.getEkycCompletedAt(),
                sv.getRiskEvaluatedAt(),
                sv.getReviewedAt(),
                reviewerId,
                sv.getRejectionReason(),
                eventHistory
        );
    }
}
