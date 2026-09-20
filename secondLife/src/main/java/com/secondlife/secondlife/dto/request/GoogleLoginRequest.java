package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
    @NotBlank(message = "Google ID token is required")
    @Schema(description = "ID token obtained from Google Sign-In on client application", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6...")
    String idToken
) {}
