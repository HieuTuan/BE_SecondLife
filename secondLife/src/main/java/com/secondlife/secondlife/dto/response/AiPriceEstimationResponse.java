package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPriceEstimationResponse {
    private String requestId;
    private BigDecimal fairPriceMin;
    private BigDecimal fairPriceMax;
    private BigDecimal suggestedPrice;
    private String modelVersion;
    private String expectedSellTime;
    private Instant createdAt;
}
