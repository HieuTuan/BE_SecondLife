package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.AiDirectPriceEstimateRequest;
import com.secondlife.secondlife.dto.response.AiDirectPriceEstimateResponse;
import com.secondlife.secondlife.service.DirectPriceEstimationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "AI Price Estimation", description = "Standalone AI price estimation API")
public class AiPriceEstimationController {

    private final DirectPriceEstimationService estimationService;

    @PostMapping({"/api/v1/ai/estimate-price", "/api/ai/estimate-price"})
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thẩm định giá sản phẩm bằng AI trực tiếp (không bắt buộc có draft)")
    public ResponseEntity<ApiResponse<AiDirectPriceEstimateResponse>> estimatePrice(
            @Valid @RequestBody AiDirectPriceEstimateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Ước lượng giá thành công", estimationService.estimatePrice(request)));
    }
}
