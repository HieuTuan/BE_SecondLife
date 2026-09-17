package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.entity.SellerVerification;
import org.springframework.stereotype.Component;

@Component
public class SellerVerificationMapper {

    public SellerVerificationResponse toResponse(SellerVerification sv) {
        if (sv == null) return null;

        String userEmail = sv.getUser() != null ? sv.getUser().getEmail() : null;
        String userFullName = (sv.getUser() != null && sv.getUser().getProfile() != null)
                ? sv.getUser().getProfile().getFullName()
                : null;
        java.util.UUID reviewedById = sv.getReviewedBy() != null ? sv.getReviewedBy().getId() : null;

        return new SellerVerificationResponse(
                sv.getId(),
                sv.getUser() != null ? sv.getUser().getId() : null,
                userEmail,
                userFullName,
                sv.getVerificationType(),
                sv.getDocumentNumber(),
                sv.getDocumentFrontUrl(),
                sv.getDocumentBackUrl(),
                sv.getStatus(),
                sv.getSubmittedAt(),
                sv.getReviewedAt(),
                reviewedById,
                sv.getRejectionReason()
        );
    }
}
