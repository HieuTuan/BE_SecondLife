package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.credit.*;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CreditPricingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_PRICING_MANAGE')")
public class AdminCreditPricingController {
    private final CreditPricingService pricingService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/credit-pricing")
    public ResponseEntity<ApiResponse<List<CreditPricingRuleResponse>>> getPrices() {
        return ResponseEntity.ok(ApiResponse.success(pricingService.getAdminPrices()));
    }

    @PutMapping("/credit-pricing/{creditType}")
    public ResponseEntity<ApiResponse<CreditPricingRuleResponse>> updatePrice(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable CreditType creditType,
            @Valid @RequestBody UpdateCreditPricingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(pricingService.updatePrice(
                currentUserProvider.resolveAdminId(admin), creditType, request)));
    }

}
