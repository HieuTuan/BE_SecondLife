package com.secondlife.secondlife.dto.response;

import java.time.Instant;

public record SellerOnboardingEmailCodeResponse(String email, Instant expiresAt, Instant resendAvailableAt) {}
