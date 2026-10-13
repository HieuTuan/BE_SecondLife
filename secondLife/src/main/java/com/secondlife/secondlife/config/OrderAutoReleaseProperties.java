package com.secondlife.secondlife.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.orders.auto-release")
public record OrderAutoReleaseProperties(boolean enabled, @Positive long delayHours,
                                         @Positive long intervalMs, @Positive int batchSize) { }
