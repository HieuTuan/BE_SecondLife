package com.secondlife.secondlife.dto.credit;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SaveCreditDiscountTierRequest(
        @NotNull @Min(1) Integer minQuantity,
        Integer maxQuantity,
        @NotNull @DecimalMin("0.0000") @DecimalMax("0.9999")
        @Digits(integer = 1, fraction = 4) BigDecimal discountRate,
        @NotNull Boolean active) {
}
