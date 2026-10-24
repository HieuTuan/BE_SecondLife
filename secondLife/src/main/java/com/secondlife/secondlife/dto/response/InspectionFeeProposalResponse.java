package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record InspectionFeeProposalResponse(
    UUID id,
    UUID categoryId,
    String categoryName,
    BigDecimal baseInspectionFee,
    String tiersJson,
    List<InspectionTravelTierDto> tiers,
    UUID proposedById,
    String proposedByName,
    String proposedByEmail,
    UUID reviewedById,
    String reviewedByName,
    InspectionFeeProposalStatus status,
    String rejectReason,
    Instant createdAt,
    Instant reviewedAt
) {
}
