package com.secondlife.secondlife.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ekyc.recovery")
public record EkycRecoveryProperties(
        boolean enabled,
        @Positive long intervalMs,
        @Positive long delayMs,
        @Positive int maxAttempts,
        @Positive int batchSize
) {
}
