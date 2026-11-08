package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.InspectionAppointmentRequest;
import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionOrderChecklistService;
import com.secondlife.secondlife.service.InspectionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspector")
@Tag(name = "Inspector Execution Station", description = "APIs dành cho Kỹ thuật viên kiểm định thực thi quy trình nghiệp vụ")
public class InspectorController {

    private final InspectionService inspectionService;
    private final InspectionOrderChecklistService orderChecklistService;
    private final CurrentUserProvider currentUserProvider;

    public InspectorController(InspectionService inspectionService,
                               InspectionOrderChecklistService orderChecklistService,
                               CurrentUserProvider currentUserProvider) {
        this.inspectionService = inspectionService;
        this.orderChecklistService = orderChecklistService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('INSPECTION_ORDER_READ_SELF')")
    @Operation(summary = "Kỹ thuật viên xem danh sách các đơn kiểm định được phân công cho mình")
    public ResponseEntity<List<InspectionOrderResponse>> getMyAssignedOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID inspectorId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.getMyOrders(inspectorId));
    }

    @GetMapping("/orders/all")
    @PreAuthorize("hasAuthority('INSPECTION_ORDER_READ_ANY') or hasAuthority('INSPECTION_ORDER_READ_SELF')")
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

    @PostMapping("/orders/{orderId}/schedule")
    @Operation(summary = "Kỹ thuật viên hoặc Người bán lên lịch hẹn ngày giờ đến nhà kiểm định (Gói AT_HOME)")
    public ResponseEntity<InspectionOrderResponse> scheduleAppointment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @Valid @RequestBody InspectionAppointmentRequest request) {
        UUID actorId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.scheduleAppointment(actorId, orderId, request));
    }

    @GetMapping("/orders/{orderId}/checklist")
    @Operation(summary = "Lấy checklist theo phiên bản đã chốt trong đơn, có kiểm tra quyền truy cập")
    public ResponseEntity<InspectionChecklistResponse> getChecklistForOrder(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderChecklistService.getForOrder(currentUserProvider.resolveUserId(principal), orderId));
    }

    @PostMapping("/orders/{orderId}/start")
    @PreAuthorize("hasAuthority('INSPECTION_REPORT_SUBMIT')")
    @Operation(summary = "Kỹ thuật viên bắt đầu tiến hành kiểm định sản phẩm (chuyển trạng thái sang INSPECTING)")
    public ResponseEntity<InspectionOrderResponse> startInspection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId) {
        UUID inspectorId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.startInspection(inspectorId, orderId));
    }

    @PostMapping(value = {"/orders/{orderId}/result", "/orders/{orderId}/submit-report"})
    @PreAuthorize("hasAuthority('INSPECTION_REPORT_SUBMIT')")
    @Operation(summary = "Kỹ thuật viên nộp biên bản kết quả kiểm định kèm video bằng chứng và chi tiết checklist")
    public ResponseEntity<InspectionOrderResponse> submitInspectionResult(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @Valid @RequestBody InspectionResultRequest request) {
        UUID inspectorId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.submitResult(orderId, inspectorId, request));
    }

    @PostMapping("/orders/{orderId}/generate-seal")
    @PreAuthorize("hasAuthority('INSPECTION_REPORT_SUBMIT')")
    @Operation(summary = "Kỹ thuật viên yêu cầu hệ thống tự sinh mã tem niêm phong số chuẩn kèm checksum")
    public ResponseEntity<ApiResponse<String>> generateSeal(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId) {
        UUID inspectorId = currentUserProvider.resolveUserId(userDetails);
        String sealCode = inspectionService.generateSealForOrder(inspectorId, orderId);
        return ResponseEntity.ok(ApiResponse.success("Sinh mã tem thành công", sealCode));
    }
}
