package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.TopupPackageRequest;
import com.secondlife.secondlife.dto.request.TopupPackageStatusRequest;
import com.secondlife.secondlife.dto.response.TopupPackageResponse;
import com.secondlife.secondlife.service.AdminTopupPackageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/topup-packages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_PRICING_MANAGE')")
@Tag(name = "Admin Topup Packages", description = "Quản trị giá và quyền lợi các gói nạp")
public class AdminTopupPackageController {
    private final AdminTopupPackageService service;

    @GetMapping
    @Operation(summary = "Lấy danh sách gói nạp cho admin")
    public ApiResponse<List<TopupPackageResponse>> getPackages() {
        return ApiResponse.success(service.getPackages());
    }

    @GetMapping("/{packageId}")
    @Operation(summary = "Xem chi tiết gói nạp")
    public ApiResponse<TopupPackageResponse> getPackage(@PathVariable("packageId") UUID packageId) {
        return ApiResponse.success(service.getPackage(packageId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo gói nạp")
    public ApiResponse<TopupPackageResponse> createPackage(@Valid @RequestBody TopupPackageRequest request) {
        return ApiResponse.success(service.createPackage(request));
    }

    @PutMapping("/{packageId}")
    @Operation(summary = "Cập nhật toàn bộ thông tin gói nạp")
    public ApiResponse<TopupPackageResponse> updatePackage(
            @PathVariable("packageId") UUID packageId, @Valid @RequestBody TopupPackageRequest request) {
        return ApiResponse.success(service.updatePackage(packageId, request));
    }

    @DeleteMapping("/{packageId}")
    @Operation(summary = "Ngừng bán gói dịch vụ", description = "Tương thích DELETE: chỉ ngừng bán, giữ cấu hình và lịch sử.")
    public ApiResponse<Void> deletePackage(@PathVariable("packageId") UUID packageId) {
        service.deletePackage(packageId);
        return ApiResponse.success("Đã ngừng bán gói dịch vụ");
    }

    @PatchMapping("/{packageId}/status")
    @Operation(summary = "Mở bán hoặc ngừng bán gói dịch vụ")
    public ApiResponse<TopupPackageResponse> setActive(
            @PathVariable("packageId") UUID packageId, @Valid @RequestBody TopupPackageStatusRequest request) {
        return ApiResponse.success(service.setActive(packageId, request.active()));
    }
}
