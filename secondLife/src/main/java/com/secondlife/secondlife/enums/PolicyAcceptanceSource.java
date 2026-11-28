package com.secondlife.secondlife.enums;

import java.util.Locale;

public enum PolicyAcceptanceSource {
    WEB,
    MOBILE_BUYER,
    MOBILE_SELLER,
    MOBILE_INSPECTOR,
    MOBILE,
    API;

    public static PolicyAcceptanceSource fromNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return API;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (PolicyAcceptanceSource candidate : values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        return API;
    }
}
