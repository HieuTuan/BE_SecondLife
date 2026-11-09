package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.InspectionReturnShippingPolicyRequest;
import com.secondlife.secondlife.dto.response.InspectionReturnShippingPolicyResponse;
import com.secondlife.secondlife.service.InspectionShippingQuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/inspection/return-shipping-policy")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('INSPECTION_FEE_MANAGE')")
public class AdminInspectionReturnShippingController {
    private final InspectionShippingQuoteService shippingQuotes;

    @GetMapping
    public ApiResponse<InspectionReturnShippingPolicyResponse> getPolicy() {
        return ApiResponse.success(shippingQuotes.getReturnPolicy());
    }

    @PutMapping
    public ApiResponse<InspectionReturnShippingPolicyResponse> updatePolicy(
            @Valid @RequestBody InspectionReturnShippingPolicyRequest request) {
        return ApiResponse.success(shippingQuotes.updateReturnPolicy(request));
    }
}
