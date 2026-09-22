package com.secondlife.secondlife.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Schema(description = "Account email address", example = "buyer@example.com")
    String email,

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be exactly 6 digits")
    @Schema(description = "6-digit OTP received via email", example = "123456")
    String otp
) {}
