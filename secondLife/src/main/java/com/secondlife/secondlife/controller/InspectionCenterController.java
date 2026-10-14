package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.CreateInspectorRequest;
import com.secondlife.secondlife.dto.request.InspectionChecklistTemplateRequest;
import com.secondlife.secondlife.dto.request.InspectionFeeProposalRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;
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
@RequestMapping("/api/v1/inspector-center")
@RequiredArgsConstructor
@Tag(name = "Inspection Center Management Portal", description = "APIs dành cho Quản lý Trung tâm kiểm định điều phối và quản trị")
public class InspectionCenterController {

    private final InspectionService inspectionService;
    private final InspectionChecklistService checklistService;
    private final InspectionPricingService pricingService;
    private final CurrentUserProvider currentUserProvider;

    // --- 1. ĐIỀU PHỐI & THEO DÕI ĐƠN KIỂM ĐỊNH ---

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('INSPECTION_ORDER_READ_ANY')")
    @Operation(summary = "Xem danh sách tất cả các đơn kiểm định (hỗ trợ lọc status và phân trang)")
    public ResponseEntity<com.secondlife.secondlife.common.ApiResponse<com.secondlife.secondlife.common.PageResponse<InspectionOrderResponse>>> getAllOrders(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(com.secondlife.secondlife.common.ApiResponse.success(
                com.secondlife.secondlife.common.PageResponse.from(inspectionService.getAllOrders(status, pageable))));
    }

    @PostMapping("/orders/{orderId}/assign")
    @PreAuthorize("hasAuthority('INSPECTION_ORDER_ASSIGN')")
    @Operation(summary = "Điều phối phân công Kỹ thuật viên (Inspector) cho đơn kiểm định")
    public ResponseEntity<InspectionOrderResponse> assignInspector(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @RequestParam UUID inspectorId) {
        UUID managerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.assignInspector(orderId, inspectorId, managerId));
    }

    // --- 2. QUẢN LÝ CHECKLIST STUDIO ---

    @GetMapping("/checklists")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Xem danh sách các bộ checklist kiểm định đang kích hoạt")
    public ResponseEntity<List<InspectionChecklistResponse>> getAllChecklists() {
        return ResponseEntity.ok(checklistService.getAllTemplates());
    }

    @GetMapping("/checklists/{id}")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Xem chi tiết một bộ checklist kiểm định theo ID")
    public ResponseEntity<InspectionChecklistResponse> getChecklistById(@PathVariable UUID id) {
        return ResponseEntity.ok(checklistService.getById(id));
    }

    @PostMapping("/checklists")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Tạo mới bộ checklist kiểm định theo danh mục sản phẩm")
    public ResponseEntity<InspectionChecklistResponse> createChecklist(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody InspectionChecklistTemplateRequest request) {
        UUID creatorId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(checklistService.createTemplate(creatorId, request));
    }

    @PutMapping("/checklists/{id}")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Cập nhật bộ checklist kiểm định")
    public ResponseEntity<InspectionChecklistResponse> updateChecklist(
            @PathVariable UUID id,
            @Valid @RequestBody InspectionChecklistTemplateRequest request) {
        return ResponseEntity.ok(checklistService.updateTemplate(id, request));
    }

    @DeleteMapping("/checklists/{id}")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Vô hiệu hóa hoặc xóa bộ checklist kiểm định")
    public ResponseEntity<Void> deleteChecklist(@PathVariable UUID id) {
        checklistService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }

    // --- 3. ĐỀ XUẤT BIỂU PHÍ & BẬC THANG DI CHUYỂN ---

    @GetMapping("/travel-tiers")
    @Operation(summary = "Xem bảng giá các bậc thang tính phí di chuyển hiện tại")
    public ResponseEntity<List<InspectionTravelTierDto>> getActiveTravelTiers() {
        return ResponseEntity.ok(pricingService.getActiveTiers());
    }

    @PostMapping("/travel-tiers/propose")
    @PreAuthorize("hasAuthority('INSPECTION_FEE_MANAGE')")
    @Operation(summary = "Gửi đề xuất điều chỉnh biểu phí cơ sở & bậc thang di chuyển tới Admin sàn duyệt")
    public ResponseEntity<InspectionFeeProposalResponse> proposeFeeAdjustment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody InspectionFeeProposalRequest request) {
        UUID proposerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(pricingService.proposeFee(proposerId, request));
    }

    @GetMapping("/travel-tiers/proposals")
    @PreAuthorize("hasAuthority('INSPECTION_FEE_MANAGE')")
    @Operation(summary = "Xem danh sách các đề xuất biểu phí đã gửi")
    public ResponseEntity<Page<InspectionFeeProposalResponse>> getFeeProposals(
            @RequestParam(required = false) InspectionFeeProposalStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(pricingService.getProposals(status, pageable));
    }

    // --- 4. QUẢN LÝ TÀI KHOẢN KỸ THUẬT VIÊN (STAFF) ---

    @PostMapping("/staff")
    @PreAuthorize("hasAuthority('INSPECTION_STAFF_MANAGE')")
    @Operation(summary = "Tạo tài khoản Kỹ thuật viên kiểm định (role INSPECTOR)")
    public ResponseEntity<String> createStaff(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateInspectorRequest request) {
        UUID managerId = currentUserProvider.resolveUserId(userDetails);
        inspectionService.createInspectorAccount(
                request.getEmail(), request.getFullName(), request.getPassword(), managerId);
        return ResponseEntity.ok("Tạo tài khoản Kỹ thuật viên thành công");
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAuthority('INSPECTION_STAFF_MANAGE')")
    @Operation(summary = "Xem danh sách các Kỹ thuật viên kiểm định")
    public ResponseEntity<List<?>> getStaffList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID managerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.getStaffList(managerId));
    }
}
