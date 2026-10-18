package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.*;
import com.secondlife.secondlife.dto.response.OrderConfirmationResponse;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.service.OrderConfirmationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/staff/orders") @RequiredArgsConstructor
@Tag(name="Staff - Order Monitoring")
@PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_DISPUTE_REVIEW')) or (hasRole('ADMIN') and hasAuthority('ADMIN_RBAC_MANAGE'))")
public class StaffOrderController {
    private final OrderConfirmationService confirmations;
    @GetMapping @Operation(summary="Monitor orders requiring seller confirmation; STAFF cannot confirm for SELLER")
    public ApiResponse<PageResponse<OrderConfirmationResponse>> queue(@RequestParam(required=false) OrderStatus status,@ParameterObject Pageable page) {
        return ApiResponse.success(PageResponse.from(confirmations.queue(status,page)));
    }
}
