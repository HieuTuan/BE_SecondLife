package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TopupPackagePurchaseRequest(
        @NotNull UUID requestId,
        @NotNull @Min(0) Long packageVersion,
        @NotNull @DecimalMin("1000") @Digits(integer = 17, fraction = 2) BigDecimal expectedPrice
) {
}
