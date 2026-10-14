package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionTravelTierDto {
    private Integer tierNumber;
    private String name;
    private BigDecimal fromKm;
    private BigDecimal toKm;
    private BigDecimal pricePerKm;
    private BigDecimal distanceChargedInTier;
    private BigDecimal feeInTier;
    private String status;
}
