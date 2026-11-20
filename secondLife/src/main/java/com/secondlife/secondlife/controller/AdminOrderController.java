package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.response.AdminOrderResponse;
import com.secondlife.secondlife.dto.response.AdminOrderStatsResponse;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.service.AdminOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin - Orders Management", description = "Monitor, inspect, and audit marketplace escrow orders")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or hasAuthority('ADMIN_RBAC_MANAGE')")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping
    @Operation(summary = "Get paginated list of all orders with multi-criteria filters for Admin")
    public ApiResponse<PageResponse<AdminOrderResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) EscrowStatus escrowStatus,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean hasInspection,
            @ParameterObject Pageable pageable) {
        return ApiResponse.success(PageResponse.from(
                adminOrderService.getAdminOrders(status, escrowStatus, search, hasInspection, pageable)
        ));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get global order statistics, GMV, and held escrow amounts")
    public ApiResponse<AdminOrderStatsResponse> getStats() {
        return ApiResponse.success(adminOrderService.getAdminOrderStats());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get comprehensive order detail for Admin")
    public ApiResponse<AdminOrderResponse> getOrderById(@PathVariable UUID id) {
        return ApiResponse.success(adminOrderService.getAdminOrderById(id));
    }
}
