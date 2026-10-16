package com.secondlife.secondlife.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.shipping.lalamove")
public class LalamoveProperties {
    private boolean enabled;
    @NotBlank private String baseUrl;
    @NotNull private String key;
    @NotNull private String secret;
    @NotBlank @Pattern(regexp = "[A-Z]{2}") private String market;
    @NotBlank @Pattern(regexp = "[a-z]{2}_[A-Z]{2}") private String language;
    @Min(1000) @Max(30000) private int timeoutMs;
    @Positive @Max(4 * 1024 * 1024) private int maxResponseCharacters;
    @Min(0) private int webhookMaxFutureSkewSeconds;

    @AssertTrue(message = "Enabled Lalamove integration requires approved gateway and matching API credentials")
    public boolean isEnabledConfigurationValid() {
        if (!enabled) return true;
        if (baseUrl == null || key == null || secret == null) return false;
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        boolean sandbox = Set.of("https://rest.sandbox.lalamove.com", "https://rest.sandbox.lalamove.com/v3").contains(normalized);
        boolean production = Set.of("https://rest.lalamove.com", "https://rest.lalamove.com/v3").contains(normalized);
        return (sandbox && key.startsWith("pk_test_") && secret.startsWith("sk_test_")
                || production && key.startsWith("pk_prod_") && secret.startsWith("sk_prod_"))
                && !key.contains(":") && !key.contains("\r") && !key.contains("\n")
                && !secret.contains("\r") && !secret.contains("\n");
    }
}
