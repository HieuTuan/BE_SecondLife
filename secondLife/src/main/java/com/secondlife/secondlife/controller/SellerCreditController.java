package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.credit.CreditBalanceResponse;
import com.secondlife.secondlife.dto.credit.CreateCreditPurchaseRequest;
import com.secondlife.secondlife.dto.credit.CreditLedgerResponse;
import com.secondlife.secondlife.dto.credit.CreditPricingResponse;
import com.secondlife.secondlife.dto.credit.CreditPurchaseResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CreditBalanceService;
import com.secondlife.secondlife.service.CreditPricingService;
import com.secondlife.secondlife.service.CreditPurchaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@Tag(name = "Seller Credits", description = "Credit pricing, balances, purchases, and ledger")
public class SellerCreditController {
    private final CreditBalanceService balanceService;
    private final CreditPricingService pricingService;
    private final CreditPurchaseService purchaseService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/credits")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('CREDIT_READ_SELF')")
    public ResponseEntity<ApiResponse<CreditBalanceResponse>> getCredits(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(ApiResponse.success(
                balanceService.getBalance(currentUserProvider.resolveUserId(currentUser))));
    }

    @GetMapping("/credit-pricing")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('CREDIT_READ_SELF')")
    public ResponseEntity<ApiResponse<CreditPricingResponse>> getPricing(
            @RequestParam(required = false) Integer listingQuantity,
            @RequestParam(required = false) Integer valuationQuantity,
            @RequestParam(required = false) Integer aiChatQuantity) {
        return ResponseEntity.ok(ApiResponse.success(
                pricingService.getPricing(listingQuantity, valuationQuantity, aiChatQuantity)));
    }

    @PostMapping("/credit-purchases")
    @Operation(summary = "Create a credit purchase using the current server unit prices")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('CREDIT_PURCHASE_SELF')")
    public ResponseEntity<ApiResponse<CreditPurchaseResponse>> createPurchase(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreateCreditPurchaseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                purchaseService.createPurchase(currentUserProvider.resolveUserId(currentUser), request)));
    }

    @GetMapping("/credit-purchases")
    @Operation(summary = "List own credit purchases")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('CREDIT_READ_SELF')")
    public ResponseEntity<ApiResponse<PageResponse<CreditPurchaseResponse>>> getPurchases(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(purchaseService.getPurchases(
                currentUserProvider.resolveUserId(currentUser), pageable)));
    }

    @GetMapping("/credit-ledger")
    @Operation(summary = "List own credit ledger entries")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('CREDIT_READ_SELF')")
    public ResponseEntity<ApiResponse<PageResponse<CreditLedgerResponse>>> getLedger(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(purchaseService.getLedger(
                currentUserProvider.resolveUserId(currentUser), pageable)));
    }
}
