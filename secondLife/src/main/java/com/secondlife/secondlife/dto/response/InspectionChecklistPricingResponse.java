package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionChecklistPriceStatus;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InspectionChecklistPricingResponse(
        UUID inspectionCenterId,
        String inspectionCenterName,
        InspectionComplexityLevel complexityLevel,
        BigDecimal baseInspectionFee,
        UUID feeProposalId,
        Instant feeReviewedAt,
        BigDecimal pendingBaseInspectionFee,
        UUID pendingFeeProposalId,
        InspectionChecklistPriceStatus priceStatus,
        boolean applicable,
        String unavailableReason
) {
}
