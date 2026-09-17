package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateInspectionCenterRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.enums.VerificationStatus;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.SellerVerificationService;
import com.secondlife.secondlife.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin - Seller Verifications", description = "Admin seller verification review and Inspection Center provisioning APIs")
public class AdminSellerVerificationController {

    private final SellerVerificationService sellerVerificationService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/seller-verifications")
    @Operation(summary = "Get seller verifications with optional status filtering and pagination")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_READ_ANY')")
    public ResponseEntity<ApiResponse<PageResponse<SellerVerificationResponse>>> getSellerVerifications(
            @RequestParam(required = false) VerificationStatus status,
            @PageableDefault(sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        PageResponse<SellerVerificationResponse> response = sellerVerificationService.getAdminVerifications(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Get seller verifications successfully", response));
    }

    @GetMapping("/seller-verifications/{id}")
    @Operation(summary = "Get seller verification details by ID")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_READ_ANY')")
    public ResponseEntity<ApiResponse<SellerVerificationResponse>> getSellerVerificationById(
            @PathVariable UUID id
    ) {
        SellerVerificationResponse response = sellerVerificationService.getAdminVerificationById(id);
        return ResponseEntity.ok(ApiResponse.success("Get seller verification details successfully", response));
    }

    @PostMapping("/seller-verifications/{id}/approve")
    @Operation(summary = "Approve seller verification and assign SELLER role")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_REVIEW')")
    public ResponseEntity<ApiResponse<SellerVerificationResponse>> approveSellerVerification(
            @AuthenticationPrincipal CustomUserDetails currentAdmin,
            @PathVariable UUID id
    ) {
        UUID adminId = currentUserProvider.resolveAdminId(currentAdmin);
        SellerVerificationResponse response = sellerVerificationService.approveVerification(adminId, id);
        return ResponseEntity.ok(ApiResponse.success("Seller verification approved and SELLER role assigned successfully", response));
    }

    @PostMapping("/seller-verifications/{id}/reject")
    @Operation(summary = "Reject seller verification with rejection reason")
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_REVIEW')")
    public ResponseEntity<ApiResponse<SellerVerificationResponse>> rejectSellerVerification(
            @AuthenticationPrincipal CustomUserDetails currentAdmin,
            @PathVariable UUID id,
            @Valid @RequestBody SellerVerificationReviewRequest request
    ) {
        UUID adminId = currentUserProvider.resolveAdminId(currentAdmin);
        SellerVerificationResponse response = sellerVerificationService.rejectVerification(adminId, id, request);
        return ResponseEntity.ok(ApiResponse.success("Seller verification rejected", response));
    }

    @PostMapping("/inspection-center-accounts")
    @Operation(summary = "Create Inspection Center partner account")
    @PreAuthorize("hasAuthority('INSPECTION_CENTER_ACCOUNT_MANAGE')")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> createInspectionCenterAccount(
            @Valid @RequestBody CreateInspectionCenterRequest request
    ) {
        UserSummaryResponse response = userService.createInspectionCenterAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Inspection Center account provisioned successfully", response));
    }
}

