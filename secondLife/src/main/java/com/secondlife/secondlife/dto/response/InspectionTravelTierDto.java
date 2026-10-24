package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record InspectionTravelTierDto(
    Integer tierNumber,
    String name,
    BigDecimal fromKm,
    BigDecimal toKm,
    BigDecimal pricePerKm,
    BigDecimal distanceChargedInTier,
    BigDecimal feeInTier,
    String status
) {
}
