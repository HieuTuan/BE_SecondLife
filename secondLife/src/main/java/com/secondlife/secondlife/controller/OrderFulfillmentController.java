package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.response.OrderFulfillmentResponse;
import com.secondlife.secondlife.security.*;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.SellerReputationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Tag(name="Order - Fulfillment")
@RestController @RequestMapping("/api/v1/orders/{orderId}") @RequiredArgsConstructor @PreAuthorize("isAuthenticated()")
public class OrderFulfillmentController {
    private final SellerReputationService reputation;
    private final CurrentUserProvider current;
    @PutMapping("/ready-for-pickup")
    @Operation(summary="SELLER declares goods ready; timestamp assigned by BE, retry keeps first timestamp")
    public ApiResponse<OrderFulfillmentResponse> ready(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable UUID orderId) {
        return ApiResponse.success(reputation.readyForPickup(current.resolveUserId(actor),orderId));
    }
    @GetMapping("/fulfillment")
    @Operation(summary="Read pickup deadline/readiness/carrier pickup; candidate lateness alone does not reduce reputation")
    public ApiResponse<OrderFulfillmentResponse> get(@AuthenticationPrincipal CustomUserDetails actor,@PathVariable UUID orderId) {
        Set<String> a=actor.getAuthorities().stream().map(x->x.getAuthority()).collect(Collectors.toSet());
        boolean staff=(a.contains("ROLE_STAFF") && a.contains("STAFF_FRAUD_REVIEW")) || (a.contains("ROLE_ADMIN") && a.contains("ADMIN_RBAC_MANAGE"));
        return ApiResponse.success(reputation.fulfillment(current.resolveUserId(actor),staff,orderId));
    }
}
