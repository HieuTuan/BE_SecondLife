package com.secondlife.secondlife.enums;

import java.util.Locale;

public enum PolicyAcceptanceContext {
    REGISTER,
    LOGIN,
    GOOGLE_LOGIN,
    POLICY_UPDATE;

    public static PolicyAcceptanceContext fromNullable(String raw, PolicyAcceptanceContext fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (PolicyAcceptanceContext candidate : values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        return fallback;
    }
}
