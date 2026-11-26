package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PayInspectionReturnFeeRequest(
        @NotNull @DecimalMin("0") BigDecimal maxAmount
) {
}
