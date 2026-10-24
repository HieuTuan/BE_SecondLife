package com.secondlife.secondlife.dto.response;

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
        String shopName,
        String shopEmail,
        String phone,
        String pickupAddress,
        VerificationType verificationType,
        String documentNumber,
        String documentNumberMasked,
        String documentFrontUrl,
        String documentBackUrl,
        String selfieUrl,
        SellerVerificationStatus status,
        EkycStatus ekycStatus,
        RiskStatus riskStatus,
        ReviewSource reviewSource,
        ReasonCode reasonCode,
        String reasonCodeDescription,
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
    /** 27-arg backwards-compatible constructor for existing tests and callers. */
    public AdminSellerVerificationDetailResponse(
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
        this(
                id,
                userId,
                userEmail,
                userFullName,
                null,
                userEmail,
                null,
                null,
                verificationType,
                documentNumberMasked,
                documentNumberMasked,
                documentFrontUrl,
                documentBackUrl,
                selfieUrl,
                status,
                ekycStatus,
                riskStatus,
                reviewSource,
                reasonCode,
                reasonCode != null ? reasonCode.getDescription() : null,
                providerName,
                providerReferenceId,
                faceMatchScore,
                livenessScore,
                documentScore,
                riskScore,
                resubmissionCount,
                submittedAt,
                ekycCompletedAt,
                riskEvaluatedAt,
                reviewedAt,
                reviewedBy,
                rejectionReason,
                eventHistory
        );
    }
}
