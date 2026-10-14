package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.common.*;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.service.SystemWalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/system-wallet")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or hasAuthority('ADMIN_COMMISSION_MANAGE')")
@Tag(name = "Admin - System Wallet", description = "Order escrow, collected shipping fees and earned commission in VND")
public class AdminSystemWalletController {
    private final SystemWalletService wallets;

    @GetMapping
    @Operation(summary = "Read system balances; shipping fees are separate from earned commission")
    public ApiResponse<SystemWalletResponse> get() { return ApiResponse.success(wallets.getWallet()); }

    @GetMapping("/transactions")
    @Operation(summary = "Read immutable order fund movements, newest first")
    public ApiResponse<PageResponse<SystemWalletTransactionResponse>> transactions(@ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.from(wallets.getTransactions(pageable)));
    }
}
