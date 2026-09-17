package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
    @NotBlank(message = "Reset token is required")
    String token,

    @NotBlank(message = "New password is required")
    @StrongPassword
    String newPassword,

    @NotBlank(message = "Confirm password is required")
    String confirmPassword
) {}
