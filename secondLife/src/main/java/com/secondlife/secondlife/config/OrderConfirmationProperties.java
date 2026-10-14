package com.secondlife.secondlife.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;

@Validated @ConfigurationProperties(prefix="app.orders.confirmation")
public record OrderConfirmationProperties(boolean enabled, @Positive long lateAfterHours,
        @Positive long cancelAfterHours,
        @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal latePenaltyPoints,
        @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal timeoutPenaltyPoints,
        @Positive long intervalMs, @Min(1) @Max(500) int batchSize) {
    @AssertTrue(message="Cancellation must be later than the confirmation deadline")
    public boolean isDeadlineOrderValid() { return cancelAfterHours>lateAfterHours; }
}
