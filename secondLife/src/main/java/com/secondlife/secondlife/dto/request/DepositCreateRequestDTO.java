package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositCreateRequestDTO(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000.0", message = "Minimum deposit amount is 10,000 VND")
    BigDecimal amount
) {
}
