package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SellerOnboardingRequest;
import com.secondlife.secondlife.dto.response.SellerOnboardingEmailCodeResponse;
import com.secondlife.secondlife.dto.response.SellerOnboardingResponse;
import java.util.UUID;

public interface SellerOnboardingService {
    SellerOnboardingResponse get(UUID userId);

    SellerOnboardingResponse save(UUID userId, SellerOnboardingRequest request);

    SellerOnboardingEmailCodeResponse sendEmailCode(UUID userId);

    SellerOnboardingResponse verifyEmailCode(UUID userId, String otp);

    void requireCompleted(UUID userId);

}
