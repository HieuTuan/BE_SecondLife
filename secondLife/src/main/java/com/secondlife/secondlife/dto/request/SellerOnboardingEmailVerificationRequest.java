package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SellerOnboardingEmailVerificationRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp) {}
