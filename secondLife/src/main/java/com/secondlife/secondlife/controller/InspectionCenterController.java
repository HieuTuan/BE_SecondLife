package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateInspectorRequest;
import com.secondlife.secondlife.dto.request.InspectionChecklistTemplateRequest;
import com.secondlife.secondlife.dto.request.InspectionFeeProposalRequest;
import com.secondlife.secondlife.dto.request.InspectionLevelFeeRequest;
import com.secondlife.secondlife.dto.request.ReviewInspectionAppealRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.enums.InspectionChecklistStatus;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspector-center")
@Tag(name = "Inspection Center Management Portal", description = "APIs dành cho Quản lý Trung tâm kiểm định điều phối và quản trị")
public class InspectionCenterController {

    private final InspectionService inspectionService;
    private final InspectionChecklistService checklistService;
    private final InspectionPricingService pricingService;
    private final CurrentUserProvider currentUserProvider;

    public InspectionCenterController(InspectionService inspectionService,
                                      InspectionChecklistService checklistService,
                                      InspectionPricingService pricingService,
                                      CurrentUserProvider currentUserProvider) {
        this.inspectionService = inspectionService;
        this.checklistService = checklistService;
        this.pricingService = pricingService;
        this.currentUserProvider = currentUserProvider;
    }

    // --- 1. ĐIỀU PHỐI & THEO DÕI ĐƠN KIỂM ĐỊNH ---

    @GetMapping(value = {"/orders", "/orders/all"})
    @PreAuthorize("hasAuthority('INSPECTION_ORDER_READ_ANY')")
    @Operation(summary = "Xem danh sách tất cả các đơn kiểm định (hỗ trợ lọc status và phân trang)")
    public ResponseEntity<ApiResponse<PageResponse<InspectionOrderResponse>>> getAllOrders(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.from(inspectionService.getAllOrders(currentUserProvider.resolveUserId(principal), status, pageable))));
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

    @PostMapping("/orders/{orderId}/review-appeal")
    @PreAuthorize("hasAuthority('INSPECTION_ORDER_ASSIGN')")
    @Operation(summary = "Quản lý thẩm duyệt khiếu nại kết quả kiểm định của người bán (UPHELD hoặc OVERTURNED)")
    public ResponseEntity<ApiResponse<InspectionOrderResponse>> reviewAppeal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @Valid @RequestBody ReviewInspectionAppealRequest request) {
        UUID reviewerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(inspectionService.reviewAppeal(reviewerId, orderId, request)));
    }

    // --- 2. QUẢN LÝ CHECKLIST STUDIO ---

    @GetMapping("/checklists")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Xem danh sách các bộ checklist kiểm định đang kích hoạt")
    public ResponseEntity<List<InspectionChecklistResponse>> getAllChecklists(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(checklistService.getTemplatesForCenter(currentUserProvider.resolveUserId(principal)));
    }

    @GetMapping("/checklists/{id}")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Xem chi tiết một bộ checklist kiểm định theo ID")
    public ResponseEntity<InspectionChecklistResponse> getChecklistById(
            @AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        return ResponseEntity.ok(checklistService.getById(currentUserProvider.resolveUserId(principal), id));
    }

    @PostMapping("/checklists")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Trung tâm gửi đề xuất checklist theo Item và cấp L1-L4; chờ Admin duyệt")
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
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody InspectionChecklistTemplateRequest request) {
        return ResponseEntity.ok(checklistService.updateTemplate(currentUserProvider.resolveUserId(principal), id, request));
    }

    @DeleteMapping("/checklists/{id}")
    @PreAuthorize("hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    @Operation(summary = "Vô hiệu hóa hoặc xóa bộ checklist kiểm định")
    public ResponseEntity<Void> deleteChecklist(@AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID id) {
        checklistService.deleteTemplate(currentUserProvider.resolveUserId(principal), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/checklists/proposals")
    @PreAuthorize("hasRole('INSPECTION_CENTER') and hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
    public ApiResponse<PageResponse<InspectionChecklistResponse>> checklistProposals(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) InspectionChecklistStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(checklistService.getProposals(
                currentUserProvider.resolveUserId(principal), status, pageable)));
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

    @PostMapping("/level-fees/propose")
    @PreAuthorize("hasRole('INSPECTION_CENTER') and hasAuthority('INSPECTION_FEE_MANAGE')")
    @Operation(summary = "Gửi đề xuất giá cho một cấp L1-L4 của trung tâm; chờ Admin duyệt")
    public InspectionFeeProposalResponse proposeLevelFee(@AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody InspectionLevelFeeRequest request) {
        return pricingService.proposeFee(currentUserProvider.resolveUserId(principal),
                new InspectionFeeProposalRequest(null, request.baseInspectionFee(), List.of(), request.complexityLevel()));
    }

    @GetMapping("/level-fees")
    @PreAuthorize("hasRole('INSPECTION_CENTER') and hasAuthority('INSPECTION_FEE_MANAGE')")
    public List<InspectionFeeProposalResponse> currentLevelFees(@AuthenticationPrincipal CustomUserDetails principal) {
        return pricingService.getCenterLevelFees(currentUserProvider.resolveUserId(principal));
    }

    @GetMapping({"/travel-tiers/proposals", "/level-fees/proposals"})
    @PreAuthorize("hasAuthority('INSPECTION_FEE_MANAGE')")
    @Operation(summary = "Xem danh sách các đề xuất biểu phí đã gửi")
    public ResponseEntity<Page<InspectionFeeProposalResponse>> getFeeProposals(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(required = false) InspectionFeeProposalStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(pricingService.getCenterProposals(currentUserProvider.resolveUserId(principal), status, pageable));
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
                request.email(), request.fullName(), request.password(), managerId);
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
