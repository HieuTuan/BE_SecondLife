package com.secondlife.secondlife.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component @Validated @Getter @Setter
@ConfigurationProperties(prefix="app.geocoding.geoapify")
public class GeocodingProperties {
    private boolean enabled;
    @NotBlank @Pattern(regexp="https://api\\.geoapify\\.com/v1/geocode/reverse") private String baseUrl;
    @NotNull private String apiKey;
    @NotBlank @Pattern(regexp="https://nominatim\\.openstreetmap\\.org/reverse") private String fallbackBaseUrl;
    @NotBlank @Pattern(regexp="[^\\r\\n]+") private String fallbackUserAgent;
    @NotBlank @Pattern(regexp="[a-z]{2}") private String language;
    @Min(1000) @Max(30000) private int timeoutMs;
    @Positive @Max(4*1024*1024) private int maxResponseCharacters;
    @AssertTrue(message="Enabled Geoapify geocoding requires an API key")
    public boolean isEnabledConfigurationValid() {
        return !enabled || apiKey!=null && apiKey.matches("[A-Za-z0-9_-]+");
    }
}
