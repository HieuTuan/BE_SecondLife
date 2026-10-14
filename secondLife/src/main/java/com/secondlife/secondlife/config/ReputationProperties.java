package com.secondlife.secondlife.config;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
@Validated @ConfigurationProperties(prefix="app.reputation")
public record ReputationProperties(@Positive int minEvaluatedOrders,@Positive int maxEvidenceImages,
                                   @Positive long handoverDeadlineHours) { }
