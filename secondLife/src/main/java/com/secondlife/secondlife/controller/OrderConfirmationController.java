package com.secondlife.secondlife.controller;
import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.reputation.OrderConfirmationResponse;
import com.secondlife.secondlife.security.*;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.OrderConfirmationService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/orders/{orderId}") @RequiredArgsConstructor
@Tag(name="Order - Confirmation") @PreAuthorize("isAuthenticated()")
public class OrderConfirmationController {
    private final OrderConfirmationService confirmations;
    private final CurrentUserProvider current;
    @PutMapping("/confirm") @PreAuthorize("hasRole('SELLER')")
    @Operation(summary="Owning SELLER confirms a paid order; no request body; retry preserves the original timestamp")
    public ApiResponse<OrderConfirmationResponse> confirm(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID orderId) {
        return ApiResponse.success(confirmations.confirm(current.resolveUserId(user),orderId));
    }
    @GetMapping("/confirmation")
    @Operation(summary="BUYER/SELLER or authorized STAFF reads confirmation deadlines and status")
    public ApiResponse<OrderConfirmationResponse> get(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID orderId) {
        var authorities=user.getAuthorities().stream().map(x->x.getAuthority()).collect(java.util.stream.Collectors.toSet());
        boolean reviewer=(authorities.contains("ROLE_STAFF") && authorities.contains("STAFF_DISPUTE_REVIEW")) ||
            (authorities.contains("ROLE_ADMIN") && authorities.contains("ADMIN_RBAC_MANAGE"));
        return ApiResponse.success(confirmations.get(current.resolveUserId(user),reviewer,orderId));
    }
}
