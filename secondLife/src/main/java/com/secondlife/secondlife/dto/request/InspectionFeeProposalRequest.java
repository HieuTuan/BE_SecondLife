package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InspectionFeeProposalRequest(
    UUID categoryId,
    @NotNull(message = "baseInspectionFee is required")
    @DecimalMin("0") @Digits(integer = 16, fraction = 2)
    BigDecimal baseInspectionFee,
    List<InspectionTravelTierDto> tiers,
    InspectionComplexityLevel complexityLevel
) {
    public InspectionFeeProposalRequest(UUID categoryId, BigDecimal baseInspectionFee,
                                       List<InspectionTravelTierDto> tiers) {
        this(categoryId, baseInspectionFee, tiers, null);
    }
}
