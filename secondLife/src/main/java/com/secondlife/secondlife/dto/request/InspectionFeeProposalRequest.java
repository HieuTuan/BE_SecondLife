package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InspectionFeeProposalRequest(
    UUID categoryId,
    @NotNull(message = "baseInspectionFee is required")
    BigDecimal baseInspectionFee,
    List<InspectionTravelTierDto> tiers
) {
}
