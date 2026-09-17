package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
    @NotBlank(message = "Current password is required")
    String currentPassword,

    @NotBlank(message = "New password is required")
    @StrongPassword
    String newPassword,

    @NotBlank(message = "Confirm password is required")
    String confirmPassword
) {}
