package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.HighValueInspectionPolicyService;
import com.secondlife.secondlife.service.PreListingInspectionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inspection")
@PreAuthorize("hasRole('SELLER')")
public class PreListingInspectionController {
    private final HighValueInspectionPolicyService policies;
    private final PreListingInspectionService preListing;
    private final CurrentUserProvider currentUser;

    @GetMapping("/high-value-policy")
    @Operation(summary = "Ngưỡng giá bắt buộc kiểm định trước khi đăng bài")
    public ApiResponse<HighValueInspectionPolicyResponse> policy() {
        return ApiResponse.success(policies.get());
    }

    @PostMapping("/posts/{postId}/pre-listing/quote")
    @Operation(summary = "Người bán lấy báo giá AT_HOME hoặc AT_CENTER cho bài chờ kiểm định")
    public ApiResponse<PreListingInspectionQuoteResponse> quote(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID postId,
            @Valid @RequestBody PreListingInspectionQuoteRequest request) {
        return ApiResponse.success(preListing.quote(currentUser.resolveUserId(principal), postId, request));
    }

    @PostMapping("/posts/{postId}/pre-listing/confirm")
    @Operation(summary = "Chốt báo giá còn hiệu lực; seller chịu toàn bộ phí, hạn thanh toán 12 giờ")
    public ApiResponse<InspectionOrderResponse> confirm(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID postId,
            @Valid @RequestBody PreListingInspectionConfirmRequest request) {
        return ApiResponse.success(preListing.confirm(currentUser.resolveUserId(principal), postId, request));
    }
}
