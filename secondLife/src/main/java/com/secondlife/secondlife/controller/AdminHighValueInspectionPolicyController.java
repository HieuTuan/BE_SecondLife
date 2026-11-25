package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.HighValueInspectionPolicyRequest;
import com.secondlife.secondlife.dto.response.HighValueInspectionPolicyResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.HighValueInspectionPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/inspection/high-value-policy")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CONFIG_MANAGE')")
public class AdminHighValueInspectionPolicyController {
    private final HighValueInspectionPolicyService policies;
    private final CurrentUserProvider currentUser;

    @GetMapping
    @Operation(summary = "Xem ngưỡng giá bắt buộc kiểm định trước khi đăng")
    public ApiResponse<HighValueInspectionPolicyResponse> get() {
        return ApiResponse.success(policies.get());
    }

    @PutMapping
    @Operation(summary = "Đổi ngưỡng giá; áp dụng cho các lượt gửi đăng mới")
    public ApiResponse<HighValueInspectionPolicyResponse> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody HighValueInspectionPolicyRequest request) {
        return ApiResponse.success(policies.update(currentUser.resolveUserId(principal), request));
    }
}
