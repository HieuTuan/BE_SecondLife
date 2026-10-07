package com.secondlife.secondlife.controller.staff;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.response.AdminSellerVerificationDetailResponse;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.RiskStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.service.SellerVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/staff/seller-verifications")
@RequiredArgsConstructor
@Tag(name = "Staff - Seller Verifications", description = "Staff verification exception inquiry (read-only, approval reserved for Admin)")
public class StaffSellerVerificationController {

    private final SellerVerificationService sellerVerificationService;

    @GetMapping
    @Operation(summary = "List seller verifications (read-only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<SellerVerificationResponse>>> list(
            @RequestParam(required = false) SellerVerificationStatus status,
            @RequestParam(required = false) EkycStatus ekycStatus,
            @RequestParam(required = false) RiskStatus riskStatus,
            @RequestParam(required = false) ReasonCode reasonCode,
            @org.springdoc.core.annotations.ParameterObject
            @SortDefault(sort = "submittedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Get seller verifications successfully",
                sellerVerificationService.getAdminVerifications(
                        status, ekycStatus, riskStatus, reasonCode, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read seller verification details by verification ID (read-only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminSellerVerificationDetailResponse>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Get seller verification details successfully",
                sellerVerificationService.getAdminVerificationById(id)));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get seller verification details by seller user ID")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN') and hasAuthority('SELLER_VERIFICATION_READ_ANY')")
    public ResponseEntity<ApiResponse<AdminSellerVerificationDetailResponse>> getByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Get seller verification by user ID successfully",
                sellerVerificationService.getStaffVerificationByUserId(userId)));
    }
}