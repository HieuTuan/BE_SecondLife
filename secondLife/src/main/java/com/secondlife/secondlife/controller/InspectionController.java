package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspector")
@RequiredArgsConstructor
@Tag(name = "Inspection", description = "APIs dành cho Inspector và Inspection Center Manager")
@PreAuthorize("hasRole('INSPECTION_CENTER')")
public class InspectionController {

    private final InspectionService inspectionService;
    private final CurrentUserProvider currentUserProvider;

    /** Inspector: Xem danh sách đơn kiểm định được phân công */
    @GetMapping("/orders")
    @Operation(summary = "Inspector xem đơn kiểm định của mình")
    public ResponseEntity<List<InspectionOrderResponse>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.getMyOrders(userId));
    }

    /** Manager: Xem tất cả đơn (có thể lọc theo status) */
    @GetMapping("/orders/all")
    @Operation(summary = "Manager xem tất cả đơn kiểm định")
    public ResponseEntity<Page<InspectionOrderResponse>> getAllOrders(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(inspectionService.getAllOrders(status, pageable));
    }

    /** Manager: Phân công nhân viên cho đơn kiểm định */
    @PostMapping("/orders/{orderId}/assign")
    @Operation(summary = "Manager phân công Inspector cho đơn kiểm định")
    public ResponseEntity<InspectionOrderResponse> assignInspector(
            @PathVariable UUID orderId,
            @RequestParam UUID inspectorId) {
        return ResponseEntity.ok(inspectionService.assignInspector(orderId, inspectorId));
    }

    /** Inspector: Nộp kết quả kiểm định */
    @PostMapping("/orders/{orderId}/result")
    @Operation(summary = "Inspector nộp kết quả kiểm định (PASSED/FAILED). Nếu PASSED → AI scan lại → ACTIVE")
    public ResponseEntity<InspectionOrderResponse> submitResult(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @Valid @RequestBody InspectionResultRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.submitResult(orderId, userId, request));
    }
}
