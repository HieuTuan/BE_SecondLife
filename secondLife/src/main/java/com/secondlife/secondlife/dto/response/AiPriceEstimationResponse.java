package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.AiPriceEstimate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AiPriceEstimationResponse(UUID estimateId, UUID postId, UUID requestId,
        BigDecimal fairPriceMin, BigDecimal fairPriceMax, BigDecimal suggestedPrice,
        String currency, String modelVersion, String expectedSellTime, Instant createdAt) {
    public static AiPriceEstimationResponse from(AiPriceEstimate e) {
        return new AiPriceEstimationResponse(e.getId(), e.getListingId(), e.getRequestId(), e.getFairPriceMin(),
                e.getFairPriceMax(), e.getSuggestedPrice(), "VND", e.getModelVersion(), e.getExpectedSellTime(), e.getCreatedAt());
    }
}
