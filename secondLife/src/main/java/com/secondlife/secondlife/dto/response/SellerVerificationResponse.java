package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.VerificationStatus;
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
    VerificationStatus status,
    Instant submittedAt,
    Instant reviewedAt,
    UUID reviewedBy,
    String rejectionReason
) {}
