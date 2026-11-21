package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record HighValueInspectionPolicyRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal thresholdPrice) {
}
