package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.InspectionCenterProfileRequest;
import com.secondlife.secondlife.dto.response.InspectionCenterProfileResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionCenterProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/inspector-center/profile")
@Tag(name = "Inspection Center Profile")
public class InspectionCenterProfileController {
    private final InspectionCenterProfileService profiles;
    private final CurrentUserProvider current;

    @GetMapping
    @PreAuthorize("hasRole('INSPECTION_CENTER') and hasAuthority('PROFILE_READ_SELF')")
    @Operation(summary = "Xem tên, hotline, địa chỉ và GPS của trung tâm hiện tại")
    public ApiResponse<InspectionCenterProfileResponse> get(@AuthenticationPrincipal CustomUserDetails principal) {
        return ApiResponse.success(profiles.getMyProfile(current.resolveUserId(principal)));
    }

    @PutMapping
    @PreAuthorize("hasRole('INSPECTION_CENTER') and hasAuthority('PROFILE_UPDATE_SELF')")
    @Operation(summary = "Cập nhật đầy đủ thông tin liên hệ và địa chỉ trung tâm",
            description = "Chỉ sửa trung tâm của tài khoản đăng nhập. Chuyến mới sử dụng địa chỉ này; vận đơn đã tạo giữ nguyên địa chỉ đã lưu.")
    public ApiResponse<InspectionCenterProfileResponse> update(@AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody InspectionCenterProfileRequest request) {
        return ApiResponse.success(profiles.updateMyProfile(current.resolveUserId(principal), request));
    }
}
