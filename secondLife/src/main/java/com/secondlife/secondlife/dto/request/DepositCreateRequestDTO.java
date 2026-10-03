package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class DepositCreateRequestDTO {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000.0", message = "Minimum deposit amount is 10,000 VND")
    private BigDecimal amount;
}
