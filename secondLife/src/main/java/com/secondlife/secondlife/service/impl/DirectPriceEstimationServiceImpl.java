package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AiDirectPriceEstimateRequest;
import com.secondlife.secondlife.dto.response.AiDirectPriceEstimateResponse;
import com.secondlife.secondlife.integration.ai.AiPriceProvider;
import com.secondlife.secondlife.mapper.AiPriceEstimationMapper;
import com.secondlife.secondlife.service.DirectPriceEstimationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DirectPriceEstimationServiceImpl implements DirectPriceEstimationService {

    private final AiPriceProvider aiPriceProvider;
    private final ObjectMapper objectMapper;
    private final AiPriceEstimationMapper mapper;

    @Override
    public AiDirectPriceEstimateResponse estimatePrice(AiDirectPriceEstimateRequest request) {
        String snapshot = buildSnapshot(request);
        AiPriceProvider.PriceSuggestion suggestion;
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            suggestion = aiPriceProvider.estimate(snapshot, request.imageUrls());
        } else {
            suggestion = aiPriceProvider.estimate(snapshot);
        }
        return mapper.toResponse(suggestion);
    }

    private String buildSnapshot(AiDirectPriceEstimateRequest request) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("title", request.title());
            if (request.category() != null) snapshot.put("category", request.category());
            if (request.brand() != null) snapshot.put("brand", request.brand());
            if (request.model() != null) snapshot.put("model", request.model());
            if (request.itemCondition() != null) snapshot.put("itemCondition", request.itemCondition());
            if (request.description() != null) snapshot.put("description", request.description());
            if (request.imageUrls() != null) snapshot.put("imageUrls", request.imageUrls());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return request.title() + " " + (request.description() != null ? request.description() : "");
        }
    }
}
