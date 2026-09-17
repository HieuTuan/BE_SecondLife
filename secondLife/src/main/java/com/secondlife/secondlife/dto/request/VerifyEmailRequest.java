package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
    @NotBlank(message = "Verification token is required")
    String token
) {}
