package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.dto.credit.CreditBalanceResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CreditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/topup")
public class TopupController {

    private final CreditService creditService;
    private final CurrentUserProvider currentUserProvider;

    public TopupController(CreditService creditService, CurrentUserProvider currentUserProvider) {
        this.creditService = creditService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/packages")
    @PreAuthorize("hasAuthority('CREDIT_READ_SELF')")
    public ResponseEntity<List<TopupPackage>> getPackages() {
        return ResponseEntity.ok(creditService.getAllTopupPackages());
    }

    @GetMapping("/my-credit")
    @PreAuthorize("hasAuthority('CREDIT_READ_SELF')")
    public ResponseEntity<CreditBalanceResponse> getMyCredit(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(creditService.getUserCredit(userId));
    }

    @PostMapping("/purchase/{packageId}")
    @PreAuthorize("hasAuthority('CREDIT_PURCHASE_SELF')")
    public ResponseEntity<CreditBalanceResponse> purchasePackage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID packageId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(creditService.purchaseTopupPackage(userId, packageId));
    }
}
