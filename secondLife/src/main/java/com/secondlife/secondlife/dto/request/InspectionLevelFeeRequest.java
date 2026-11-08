package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.enums.InspectionComplexityLevel;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InspectionLevelFeeRequest(
        @NotNull InspectionComplexityLevel complexityLevel,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal baseInspectionFee
) {
}
