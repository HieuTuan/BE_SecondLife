package com.secondlife.secondlife.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "app.vnpt")
public record VnptEkycProperties(
        @NotBlank(message = "VNPT_BASE_URL is required") String baseUrl,
        @NotBlank(message = "VNPT_CLIENT_ID is required") String clientId,
        @NotBlank(message = "VNPT_CLIENT_SECRET is required") String clientSecret,
        @NotBlank(message = "VNPT_TOKEN_ID is required; it is not the client ID") String tokenId,
        @NotBlank(message = "VNPT_TOKEN_KEY is required; it is not the client secret") String tokenKey,
        @NotBlank(message = "VNPT_MAC_ADDRESS is required") String macAddress,
        @Min(1000) int timeoutMs) {
    public VnptEkycProperties {
        if (baseUrl != null && !baseUrl.isBlank()) {
            URI uri = URI.create(baseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("VNPT_BASE_URL must be an absolute HTTPS URL");
            }
            baseUrl = baseUrl.replaceAll("/+$", "");
        }
    }
}
