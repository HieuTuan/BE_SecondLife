package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.response.SystemWalletResponse;
import com.secondlife.secondlife.dto.response.SystemWalletTransactionResponse;
import com.secondlife.secondlife.enums.SystemWalletFundCategory;
import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import com.secondlife.secondlife.service.SystemWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/system-wallet")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or hasAuthority('ADMIN_COMMISSION_MANAGE')")
@Tag(name = "Admin - System Wallet", description = "Order escrow, collected shipping fees and earned commission in VND")
public class AdminSystemWalletController {
    private final SystemWalletService wallets;

    @GetMapping
    @Operation(summary = "Read product escrow, collected shipping, paid Hub fees and earned commission")
    public ApiResponse<SystemWalletResponse> get() { return ApiResponse.success(wallets.getWallet()); }

    @GetMapping("/transactions")
    @Operation(summary = "Read immutable order fund movements with optional filters")
    public ApiResponse<PageResponse<SystemWalletTransactionResponse>> transactions(
            @RequestParam(required = false) SystemWalletTransactionType type,
            @Parameter(description = "True selects nonzero shipping movements, false selects zero, omit for both")
            @RequestParam(required = false) Boolean hasShipping,
            @Parameter(description = "Order or inspection order UUID")
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) SystemWalletFundCategory fundCategory,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.from(wallets.getTransactions(type, hasShipping, orderId, fundCategory, pageable)));
    }
}
