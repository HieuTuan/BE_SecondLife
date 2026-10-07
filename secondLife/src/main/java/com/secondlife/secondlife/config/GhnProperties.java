package com.secondlife.secondlife.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.shipping.ghn")
public class GhnProperties {
    private boolean enabled;
    @NotBlank private String baseUrl;
    @NotNull private String token;
    @Min(0) private int shopId;
    @NotNull private String webhookSecret;
    @Min(1000) @Max(30000) private int timeoutMs;
    @Min(60) @Max(3600) private int quoteTtlSeconds;
    @Positive private int maxResponseCharacters;
    @Min(0) private int webhookMaxFutureSkewSeconds;

    @AssertTrue(message = "Enabled GHN integration requires token, shop ID and webhook secret")
    public boolean isEnabledConfigurationValid() {
        return !enabled || (token != null && !token.isBlank() && !token.contains("\r") && !token.contains("\n")
                && shopId > 0 && webhookSecret != null && !webhookSecret.isBlank());
    }
}
