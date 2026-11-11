package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse;
import com.secondlife.secondlife.service.AdminInspectionFundsService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/inspection/orders")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('INSPECTION_FEE_MANAGE')")
public class AdminInspectionFundsController {
    private final AdminInspectionFundsService fundsService;

    @GetMapping("/{inspectionOrderId}/funds")
    @Operation(summary = "Read collected, unsettled and settled amounts for an inspection order",
            description = "Read-only inspection accounting in VND, including the return reserve and cash shipping.")
    public ApiResponse<AdminInspectionFundsResponse> getFunds(@PathVariable UUID inspectionOrderId) {
        return ApiResponse.success(fundsService.getFunds(inspectionOrderId));
    }
}
