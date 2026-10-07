package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationResubmitRequest;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.SellerVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/seller-verifications")
@RequiredArgsConstructor
@Tag(name = "Seller Verification", description = "Seller identity verification submission and status APIs")
public class SellerVerificationController {

    private final SellerVerificationService sellerVerificationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @Operation(summary = "Submit seller verification request")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_SUBMIT')")
    public ResponseEntity<ApiResponse<SellerVerificationResponse>> submitVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SellerVerificationRequest request
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        SellerVerificationResponse response = sellerVerificationService.submitVerification(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Hồ sơ xác thực người bán đã được tiếp nhận và xử lý", response));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current seller verification status (Approved, Rejected with reason, or Resubmit Required)")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_READ_SELF')")
    public ResponseEntity<ApiResponse<SellerVerificationResponse>> getMyVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        SellerVerificationResponse response = sellerVerificationService.getCurrentVerification(userId);
        if (response == null) {
            return ResponseEntity.ok(ApiResponse.success("Tài khoản chưa có yêu cầu xác thực người bán nào", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin hồ sơ xác thực thành công", response));
    }

    @GetMapping("/me/history")
    @Operation(summary = "Get full verification history of the current seller")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_READ_SELF')")
    public ResponseEntity<ApiResponse<List<SellerVerificationResponse>>> getMyVerificationHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        List<SellerVerificationResponse> history = sellerVerificationService.getVerificationHistory(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử xác thực thành công", history));
    }

    @GetMapping("/me/status/{status}")
    @Operation(summary = "Get verification request of current seller filtered by specific status (APPROVED, REJECTED, RESUBMIT_REQUIRED, etc.)")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_READ_SELF')")
    public ResponseEntity<ApiResponse<SellerVerificationResponse>> getMyVerificationByStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable SellerVerificationStatus status
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        SellerVerificationResponse response = sellerVerificationService.getVerificationByStatus(userId, status);
        if (response == null) {
            return ResponseEntity.ok(ApiResponse.success("Không tìm thấy yêu cầu xác thực nào với trạng thái: " + status, null));
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin xác thực theo trạng thái thành công", response));
    }

    @GetMapping("/{verificationId}")
    @Operation(summary = "Get details of a specific seller verification request")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_READ_SELF')")
    public ResponseEntity<ApiResponse<SellerVerificationResponse>> getVerificationById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID verificationId
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        SellerVerificationResponse response = sellerVerificationService.getVerificationById(userId, verificationId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết hồ sơ xác thực thành công", response));
    }

    @PostMapping("/{verificationId}/resubmit")
    @Operation(summary = "Resubmit documents for a verification requiring resubmission")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_SUBMIT')")
    public ResponseEntity<ApiResponse<SellerVerificationResponse>> resubmitVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID verificationId,
            @Valid @RequestBody SellerVerificationResubmitRequest request
    ) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        SellerVerificationResponse response = sellerVerificationService.resubmitVerification(userId, verificationId, request);
        return ResponseEntity.ok(ApiResponse.success("Nộp lại chứng từ xác thực thành công", response));
    }
}