package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDirectPriceEstimateResponse {
    private BigDecimal fairPriceMin;
    private BigDecimal fairPriceMax;
    private BigDecimal suggestedPrice;
    private String expectedSellTime;
    private String modelVersion;
}
