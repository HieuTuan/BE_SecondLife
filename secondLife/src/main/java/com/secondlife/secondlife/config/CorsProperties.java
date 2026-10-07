package com.secondlife.secondlife.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(@NotBlank String allowedOrigins, @NotBlank String allowedMethods,
        @NotBlank String allowedHeaders, @NotNull Boolean allowCredentials,
        @NotNull @Min(0) Long maxAgeSeconds) {
}
