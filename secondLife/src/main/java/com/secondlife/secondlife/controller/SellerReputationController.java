package com.secondlife.secondlife.controller;
import com.secondlife.secondlife.common.*;
import com.secondlife.secondlife.dto.reputation.*;
import com.secondlife.secondlife.security.*;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.SellerReputationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
@Tag(name="Seller - Reputation")
public class SellerReputationController {
    private final SellerReputationService reputation;
    private final CurrentUserProvider currentUser;
    @GetMapping("/sellers/{sellerId}/reputation")
    @Operation(summary="Actual transaction success score: 100 * good / evaluated", description="No score when evaluated=0; fewer than configured minimum orders is provisional. Only ended orders count. Confirmed seller faults count once, serious warnings remain separate. Pickup and non-fulfillment rates expose their denominators.")
    public ApiResponse<ReputationResponse> publicReputation(@PathVariable UUID sellerId) {
        return ApiResponse.success(reputation.get(sellerId));
    }
    @GetMapping("/seller/reputation/me")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('PROFILE_READ_SELF')")
    public ApiResponse<ReputationResponse> mine(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.success(reputation.get(currentUser.resolveUserId(user)));
    }
    @GetMapping("/seller/reputation/me/history")
    @PreAuthorize("hasRole('SELLER') and hasAuthority('PROFILE_READ_SELF')")
    @Operation(summary="Read own immutable score history; imported legacy entries have no historical score snapshot")
    public ApiResponse<PageResponse<ReputationHistoryResponse>> history(@AuthenticationPrincipal CustomUserDetails user,Pageable pageable) {
        return ApiResponse.success(reputation.history(currentUser.resolveUserId(user),pageable));
    }
    @PostMapping("/admin/sellers/{sellerId}/reputation/lift-restriction")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('USER_STATUS_UPDATE')")
    @Operation(summary="Lift selling restriction with audit reason; confirmed violation history remains")
    public ApiResponse<ReputationResponse> lift(@AuthenticationPrincipal CustomUserDetails user,@PathVariable UUID sellerId,
            @Valid @RequestBody ReportRequests.LiftRestriction request) {
        return ApiResponse.success(reputation.liftRestriction(sellerId,currentUser.resolveUserId(user),request.reason()));
    }
}
