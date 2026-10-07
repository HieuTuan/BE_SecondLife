package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPriceEstimationResponse {
    private UUID estimateId;
    private String requestId;
    private BigDecimal fairPriceMin;
    private BigDecimal fairPriceMax;
    private BigDecimal suggestedPrice;
    private String modelVersion;
    private String expectedSellTime;
    private Instant createdAt;

    public static AiPriceEstimationResponse from(com.secondlife.secondlife.entity.AiPriceEstimate estimate) {
        return AiPriceEstimationResponse.builder()
                .estimateId(estimate.getId())
                .requestId(estimate.getRequestId() == null ? null : estimate.getRequestId().toString())
                .fairPriceMin(estimate.getFairPriceMin())
                .fairPriceMax(estimate.getFairPriceMax())
                .suggestedPrice(estimate.getSuggestedPrice())
                .modelVersion(estimate.getModelVersion())
                .expectedSellTime(estimate.getExpectedSellTime())
                .createdAt(estimate.getCreatedAt())
                .build();
    }
}
