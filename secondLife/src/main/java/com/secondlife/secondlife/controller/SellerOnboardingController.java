package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.SellerOnboardingRequest;
import com.secondlife.secondlife.dto.request.SellerOnboardingEmailVerificationRequest;
import com.secondlife.secondlife.dto.response.SellerOnboardingResponse;
import com.secondlife.secondlife.dto.response.SellerOnboardingEmailCodeResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.SellerOnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seller-onboarding")
@Tag(name = "Seller Onboarding", description = "Shop information and email verification before seller eKYC")
public class SellerOnboardingController {
    private final SellerOnboardingService onboarding;
    private final CurrentUserProvider currentUser;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_READ_SELF')")
    @Operation(summary = "Read shop information and the next onboarding step")
    public ApiResponse<SellerOnboardingResponse> get(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.success(onboarding.get(currentUser.resolveUserId(user)));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_SUBMIT')")
    @Operation(summary = "Save shop name, pickup address, email and phone before eKYC")
    public ApiResponse<SellerOnboardingResponse> save(@AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody SellerOnboardingRequest request) {
        return ApiResponse.success(onboarding.save(currentUser.resolveUserId(user), request));
    }

    @PostMapping("/email/send-code")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_SUBMIT')")
    @Operation(summary = "Send a 6-digit code to the saved shop email")
    public ApiResponse<SellerOnboardingEmailCodeResponse> sendCode(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.success(onboarding.sendEmailCode(currentUser.resolveUserId(user)));
    }

    @PostMapping("/email/verify")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_SUBMIT')")
    @Operation(summary = "Verify shop email and enable the eKYC step")
    public ApiResponse<SellerOnboardingResponse> verify(@AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody SellerOnboardingEmailVerificationRequest request) {
        return ApiResponse.success(onboarding.verifyEmailCode(currentUser.resolveUserId(user), request.otp()));
    }
}
