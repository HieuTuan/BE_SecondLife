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

public record SellerVerificationResponse(
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
        Double faceMatchScore,
        Double livenessScore,
        Double documentScore,
        Double riskScore,
        int resubmissionCount,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedBy,
        String rejectionReason,
        List<VerificationEventResponse> eventHistory
) {
    /** 19-arg backwards-compatible constructor for existing tests and legacy callers. */
    public SellerVerificationResponse(
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
                documentNumber,
                documentNumber,
                documentFrontUrl,
                documentBackUrl,
                selfieUrl,
                status,
                ekycStatus,
                riskStatus,
                reviewSource,
                reasonCode,
                reasonCode != null ? reasonCode.getDescription() : null,
                null,
                null,
                null,
                null,
                resubmissionCount,
                submittedAt,
                reviewedAt,
                reviewedBy,
                rejectionReason,
                List.of()
        );
    }

    public static SellerVerificationResponse from(
            SellerVerification sv,
            String shopName,
            String shopEmail,
            String phone,
            String pickupAddress,
            List<VerificationEventResponse> eventHistory) {
        String email = sv.getUser() != null ? sv.getUser().getEmail() : null;
        String fullName = (sv.getUser() != null && sv.getUser().getProfile() != null)
                ? sv.getUser().getProfile().getFullName() : null;
        String resolvedPhone = (phone != null && !phone.isBlank())
                ? phone
                : ((sv.getUser() != null && sv.getUser().getProfile() != null) ? sv.getUser().getProfile().getPhone() : null);
        String resolvedShopEmail = (shopEmail != null && !shopEmail.isBlank()) ? shopEmail : email;
        UUID reviewerId = sv.getReviewedBy() != null ? sv.getReviewedBy().getId() : null;
        String docNum = sv.getDocumentNumber() != null ? sv.getDocumentNumber() : sv.getDocumentNumberMasked();
        String reasonDesc = sv.getReasonCode() != null ? sv.getReasonCode().getDescription() : null;

        return new SellerVerificationResponse(
                sv.getId(),
                sv.getUser() != null ? sv.getUser().getId() : null,
                email,
                fullName,
                shopName,
                resolvedShopEmail,
                resolvedPhone,
                pickupAddress,
                sv.getVerificationType(),
                docNum,
                sv.getDocumentNumberMasked(),
                sv.getDocumentFrontUrl(),
                sv.getDocumentBackUrl(),
                sv.getSelfieUrl(),
                sv.getStatus(),
                sv.getEkycStatus(),
                sv.getRiskStatus(),
                sv.getReviewSource(),
                sv.getReasonCode(),
                reasonDesc,
                sv.getFaceMatchScore(),
                sv.getLivenessScore(),
                sv.getDocumentScore(),
                sv.getRiskScore(),
                sv.getResubmissionCount(),
                sv.getSubmittedAt(),
                sv.getReviewedAt(),
                reviewerId,
                sv.getRejectionReason(),
                eventHistory != null ? eventHistory : List.of()
        );
    }

    public static SellerVerificationResponse from(SellerVerification sv) {
        return from(sv, null, null, null, null, List.of());
    }
}