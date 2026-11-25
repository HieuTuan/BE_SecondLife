package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.InspectionReturnShippingPolicyRequest;
import com.secondlife.secondlife.dto.response.InspectionReturnShippingPolicyHistoryResponse;
import com.secondlife.secondlife.dto.response.InspectionReturnShippingPolicyResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionShippingQuoteService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/inspection/return-shipping-policy")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('INSPECTION_FEE_MANAGE')")
public class AdminInspectionReturnShippingController {
    private final InspectionShippingQuoteService shippingQuotes;
    private final CurrentUserProvider currentUser;

    @GetMapping
    public ApiResponse<InspectionReturnShippingPolicyResponse> getPolicy() {
        return ApiResponse.success(shippingQuotes.getReturnPolicy());
    }

    @PutMapping
    public ApiResponse<InspectionReturnShippingPolicyResponse> updatePolicy(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody InspectionReturnShippingPolicyRequest request) {
        return ApiResponse.success(shippingQuotes.updateReturnPolicy(currentUser.resolveUserId(principal), request));
    }

    @GetMapping("/history")
    @Operation(summary = "Xem lịch sử đổi tỷ lệ dự phòng trả hàng, mới nhất trước",
            description = "changedAt là thời điểm UTC; mỗi mục chứa tỷ lệ trước và sau khi đổi cùng ID quản trị viên.")
    public ApiResponse<PageResponse<InspectionReturnShippingPolicyHistoryResponse>> getHistory(
            @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.from(shippingQuotes.getReturnPolicyHistory(pageable)));
    }
}
