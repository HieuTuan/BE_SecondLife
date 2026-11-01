package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.TopupPackagePurchaseRequest;
import com.secondlife.secondlife.dto.response.CreditBalanceResponse;
import com.secondlife.secondlife.dto.response.TopupPackagePurchaseResponse;
import com.secondlife.secondlife.dto.response.TopupPackageResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CreditService;
import com.secondlife.secondlife.service.TopupPackagePurchaseService;
import com.secondlife.secondlife.service.TopupPackageQueryService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/topup")
public class TopupController {

    private final CreditService creditService;
    private final TopupPackageQueryService packageQueryService;
    private final CurrentUserProvider currentUserProvider;
    private final TopupPackagePurchaseService purchases;

    public TopupController(CreditService creditService, TopupPackageQueryService packageQueryService,
                           CurrentUserProvider currentUserProvider, TopupPackagePurchaseService purchases) {
        this.creditService = creditService;
        this.packageQueryService = packageQueryService;
        this.currentUserProvider = currentUserProvider;
        this.purchases = purchases;
    }

    @GetMapping("/packages")
    @PreAuthorize("hasAuthority('CREDIT_READ_SELF')")
    public ResponseEntity<List<TopupPackageResponse>> getPackages() {
        return ResponseEntity.ok(packageQueryService.getPackages());
    }

    @GetMapping("/my-credit")
    @PreAuthorize("hasAuthority('CREDIT_READ_SELF')")
    public ResponseEntity<CreditBalanceResponse> getMyCredit(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(creditService.getUserCredit(userId));
    }

    @PostMapping("/purchase/{packageId}")
    @PreAuthorize("hasAuthority('CREDIT_PURCHASE_SELF')")
    @Operation(summary = "Mua gói bằng ví", description = "Gửi lại cùng requestId và nội dung để nhận lại biên nhận, không trừ tiền lần hai.")
    public ApiResponse<TopupPackagePurchaseResponse> purchasePackage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("packageId") UUID packageId, @Valid @RequestBody TopupPackagePurchaseRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ApiResponse.success(purchases.purchase(userId, packageId, request));
    }

    @GetMapping("/purchases")
    @PreAuthorize("hasAuthority('CREDIT_READ_SELF')")
    public ApiResponse<PageResponse<TopupPackagePurchaseResponse>> history(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(purchases.getMyPurchases(currentUserProvider.resolveUserId(userDetails), page, size));
    }

    @GetMapping("/purchases/{purchaseId}")
    @PreAuthorize("hasAuthority('CREDIT_READ_SELF')")
    public ApiResponse<TopupPackagePurchaseResponse> detail(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable("purchaseId") UUID purchaseId) {
        return ApiResponse.success(purchases.getMyPurchase(currentUserProvider.resolveUserId(userDetails), purchaseId));
    }
}
