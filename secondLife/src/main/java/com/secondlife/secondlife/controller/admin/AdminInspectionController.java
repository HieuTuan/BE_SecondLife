package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.ReviewFeeProposalRequest;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionPricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/inspection")
@RequiredArgsConstructor
@Tag(name = "Admin Inspection Governance", description = "APIs dành cho Admin Sàn quản lý biểu phí và bảng giá bậc thang kiểm định")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('INSPECTION_FEE_MANAGE')")
public class AdminInspectionController {

    private final InspectionPricingService pricingService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/fee-proposals")
    @Operation(summary = "Admin xem danh sách các đề xuất biểu phí từ Trung tâm kiểm định (lọc theo status)")
    public ResponseEntity<ApiResponse<com.secondlife.secondlife.common.PageResponse<InspectionFeeProposalResponse>>> getFeeProposals(
            @RequestParam(required = false) InspectionFeeProposalStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                com.secondlife.secondlife.common.PageResponse.from(pricingService.getProposals(status, pageable))));
    }

    @PostMapping("/fee-proposals/{id}/review")
    @Operation(summary = "Admin phê duyệt hoặc từ chối đề xuất biểu phí kiểm định. Khi APPROVED sẽ cập nhật các bậc thang")
    public ResponseEntity<ApiResponse<InspectionFeeProposalResponse>> reviewFeeProposal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID id,
            @Valid @RequestBody ReviewFeeProposalRequest request) {
        UUID adminId = currentUserProvider.resolveAdminId(userDetails);
        InspectionFeeProposalResponse res = pricingService.reviewProposal(
                id, adminId, request.getApproved(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Xét duyệt đề xuất biểu phí thành công", res));
    }

    @GetMapping("/travel-tiers")
    @Operation(summary = "Admin xem bảng 6 bậc thang tính phí di chuyển hiện tại")
    public ResponseEntity<ApiResponse<List<InspectionTravelTierDto>>> getTravelTiers() {
        return ResponseEntity.ok(ApiResponse.success(pricingService.getActiveTiers()));
    }

    @PutMapping("/travel-tiers")
    @Operation(summary = "Admin chủ động cập nhật trực tiếp giá các bậc thang di chuyển KTV")
    public ResponseEntity<ApiResponse<List<InspectionTravelTierDto>>> updateTravelTiers(
            @Valid @RequestBody List<InspectionTravelTierDto> tiers) {
        List<InspectionTravelTierDto> updated = pricingService.updateTiers(tiers);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật biểu phí bậc thang di chuyển thành công", updated));
    }
}
