package com.secondlife.secondlife.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.seeder.inspection-center-2")
public record InspectionCenterSeedProperties(
        String email,
        String password,
        String fullName,
        String phone,
        String province,
        String ward,
        String streetAddress,
        BigDecimal latitude,
        BigDecimal longitude
) {
    @Override
    public String toString() {
        return "InspectionCenterSeedProperties[email=" + email + ", password=[REDACTED]]";
    }
}
