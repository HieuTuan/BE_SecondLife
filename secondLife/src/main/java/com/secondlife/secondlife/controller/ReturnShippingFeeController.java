package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.response.ReturnShippingFeeResponse;
import com.secondlife.secondlife.dto.response.SellerReturnShippingDebtResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ReturnShippingFeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Seller - Return Shipping Fees")
public class ReturnShippingFeeController {
    private final ReturnShippingFeeService fees;
    private final CurrentUserProvider currentUser;

    @GetMapping("/shipments/{shipmentId}/return-shipping-fee")
    @Operation(summary = "Read a private return shipping fee assessment", description = "Only the shipment seller or authorized STAFF/ADMIN may read the collected amount and debt.")
    public ApiResponse<ReturnShippingFeeResponse> get(@AuthenticationPrincipal CustomUserDetails user,
            Authentication authentication, @PathVariable UUID shipmentId) {
        var authorities = authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.toSet());
        boolean reviewer = (authorities.contains("ROLE_STAFF") && authorities.contains("STAFF_DISPUTE_REVIEW"))
                || (authorities.contains("ROLE_ADMIN") && authorities.contains("ADMIN_RBAC_MANAGE"));
        return ApiResponse.success(fees.get(currentUser.resolveUserId(user), reviewer, shipmentId));
    }

    @GetMapping("/seller/return-shipping-fees")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('PROFILE_READ_SELF')")
    @Operation(summary = "List own return shipping fee assessments", description = "Fixed order: createdAt DESC, id DESC. outstandingOnly includes only assessed fees with unpaid debt.")
    @Parameters({
        @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page; defaults to app.pagination.default-page", example = "0", schema = @Schema(type = "integer", minimum = "0")),
        @Parameter(name = "size", in = ParameterIn.QUERY, description = "Page size; default and maximum follow app.pagination configuration", example = "20", schema = @Schema(type = "integer", minimum = "1"))
    })
    public ApiResponse<PageResponse<ReturnShippingFeeResponse>> mine(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "false") boolean outstandingOnly, @Parameter(hidden = true) Pageable pageable) {
        return ApiResponse.success(fees.mine(currentUser.resolveUserId(user), outstandingOnly, pageable));
    }

    @GetMapping("/seller/return-shipping-fees/debt")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('PROFILE_READ_SELF')")
    @Operation(summary = "Read own outstanding return shipping debt", description = "Sum of unpaid amounts from immutable assessments; pending carrier fees are not included.")
    public ApiResponse<SellerReturnShippingDebtResponse> debt(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.success(fees.debt(currentUser.resolveUserId(user)));
    }
}
