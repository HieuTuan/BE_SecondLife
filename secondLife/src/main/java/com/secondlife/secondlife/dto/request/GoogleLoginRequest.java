package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record GoogleLoginRequest(
    @NotBlank(message = "Google ID token is required")
    @Schema(description = "ID token obtained from Google Sign-In on client application", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...")
    String idToken,

    @Schema(description = "Explicit user confirmation of current Terms of Service & Operating Policy", example = "true")
    Boolean acceptTerms,

    @Schema(description = "ID of the active Terms of Service policy version accepted by the user")
    UUID acceptedPolicyVersionId,

    @Schema(description = "Client source: WEB, MOBILE_BUYER, MOBILE_SELLER, MOBILE_INSPECTOR, MOBILE, API", example = "WEB")
    String acceptanceSource
) {
    public GoogleLoginRequest(String idToken) {
        this(idToken, null, null, null);
    }
}
