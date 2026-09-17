package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    String email
) {}
