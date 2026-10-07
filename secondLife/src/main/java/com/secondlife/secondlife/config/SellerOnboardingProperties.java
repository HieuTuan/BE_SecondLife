package com.secondlife.secondlife.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "app.seller-onboarding.email")
public class SellerOnboardingProperties {
    @Min(60) @Max(3600) private int otpTtlSeconds;
    @Min(1) @Max(3600) private int resendCooldownSeconds;
    @Min(1) @Max(10) private int maxAttempts;
}
