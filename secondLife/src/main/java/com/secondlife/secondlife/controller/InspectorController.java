package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "Lấy biểu mẫu checklist theo danh mục của sản phẩm trong đơn để KTV thực hiện chấm điểm")
    public ResponseEntity<InspectionChecklistResponse> getChecklistForOrder(
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderChecklistService.getForOrder(orderId));
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
