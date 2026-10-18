package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AiDirectPriceEstimateResponse(
    BigDecimal fairPriceMin,
    BigDecimal fairPriceMax,
    BigDecimal suggestedPrice,
    String expectedSellTime,
    String modelVersion
) {
}
