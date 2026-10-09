package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.commission.*;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Set;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/orders/{orderId}") @RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Order - Commission and Settlement")
public class OrderSettlementController {
    private final SettlementService settlements;
    private final CurrentUserProvider currentUser;

    @GetMapping("/commission")
    @Operation(summary = "Read immutable policy snapshot and calculated net seller amount")
    public ApiResponse<OrderCommissionResponse> commission(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID orderId) {
        return ApiResponse.success(settlements.getCommission(currentUser.resolveUserId(user), canReadAny(user), orderId));
    }
    @GetMapping("/settlement")
    @Operation(summary = "Read final settlement created by PUT /api/v1/orders/{id}/delivered; 404 before settlement")
    public ApiResponse<SettlementResponse> settlement(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID orderId) {
        return ApiResponse.success(settlements.getSettlement(currentUser.resolveUserId(user), canReadAny(user), orderId));
    }
    private boolean canReadAny(CustomUserDetails user) {
        Set<String> authorities = user.getAuthorities().stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        return (authorities.contains("ROLE_ADMIN") && authorities.contains("ADMIN_COMMISSION_MANAGE"))
                || (authorities.contains("ROLE_STAFF") && authorities.contains("STAFF_PAYOUT_REVIEW"));
    }
}
