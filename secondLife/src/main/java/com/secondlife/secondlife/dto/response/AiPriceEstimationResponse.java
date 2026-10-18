package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record AiPriceEstimationResponse(
    UUID estimateId,
    String requestId,
    BigDecimal fairPriceMin,
    BigDecimal fairPriceMax,
    BigDecimal suggestedPrice,
    String modelVersion,
    String expectedSellTime,
    Instant createdAt
) {
}
