package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.AiDirectPriceEstimateRequest;
import com.secondlife.secondlife.dto.response.AiDirectPriceEstimateResponse;
import com.secondlife.secondlife.service.AiPriceProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Tag(name = "AI Price Estimation", description = "Standalone AI price estimation API")
public class AiPriceEstimationController {

    private final AiPriceProvider aiPriceProvider;
    private final ObjectMapper objectMapper;

    public AiPriceEstimationController(AiPriceProvider aiPriceProvider, ObjectMapper objectMapper) {
        this.aiPriceProvider = aiPriceProvider;
        this.objectMapper = objectMapper;
    }

    @PostMapping({"/api/v1/ai/estimate-price", "/api/ai/estimate-price"})
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Thẩm định giá sản phẩm bằng AI trực tiếp (không bắt buộc có draft)")
    public ResponseEntity<ApiResponse<AiDirectPriceEstimateResponse>> estimatePrice(
            @Valid @RequestBody AiDirectPriceEstimateRequest request) {

        String snapshot = buildSnapshot(request);
        AiPriceProvider.PriceSuggestion suggestion;

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            suggestion = aiPriceProvider.estimate(snapshot, request.getImageUrls());
        } else {
            suggestion = aiPriceProvider.estimate(snapshot);
        }

        AiDirectPriceEstimateResponse response = AiDirectPriceEstimateResponse.builder()
                .fairPriceMin(suggestion.min())
                .fairPriceMax(suggestion.max())
                .suggestedPrice(suggestion.suggested())
                .expectedSellTime(suggestion.expectedSellTime())
                .modelVersion(suggestion.modelVersion())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Ước lượng giá thành công", response));
    }

    private String buildSnapshot(AiDirectPriceEstimateRequest req) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("title", req.getTitle());
            if (req.getCategory() != null) map.put("category", req.getCategory());
            if (req.getBrand() != null) map.put("brand", req.getBrand());
            if (req.getModel() != null) map.put("model", req.getModel());
            if (req.getItemCondition() != null) map.put("itemCondition", req.getItemCondition());
            if (req.getDescription() != null) map.put("description", req.getDescription());
            if (req.getImageUrls() != null) map.put("imageUrls", req.getImageUrls());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return req.getTitle() + " " + (req.getDescription() != null ? req.getDescription() : "");
        }
    }
}
