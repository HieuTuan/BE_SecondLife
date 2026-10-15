package com.secondlife.secondlife.config;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated @ConfigurationProperties(prefix="app.orders.bank-transfer")
public record OrderBankTransferProperties(@Min(1) @Max(1440) int timeoutMinutes,
        @Positive long intervalMs, @Min(1) @Max(500) int batchSize) {}
