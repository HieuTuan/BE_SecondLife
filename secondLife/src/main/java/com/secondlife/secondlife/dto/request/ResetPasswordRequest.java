package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ResetPasswordRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Schema(description = "Account email address", example = "buyer@example.com")
    String email,

    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^[0-9]{6}$", message = "OTP must be exactly 6 digits")
    @Schema(description = "6-digit OTP received via email", example = "123456")
    String otp,

    @NotBlank(message = "New password is required")
    @StrongPassword
    @Schema(description = "New password (min 8 chars, uppercase, lowercase, digit, special char)", example = "NewSecretPass@123")
    String newPassword,

    @NotBlank(message = "Confirm password is required")
    @Schema(description = "Confirm new password", example = "NewSecretPass@123")
    String confirmPassword
) {}
