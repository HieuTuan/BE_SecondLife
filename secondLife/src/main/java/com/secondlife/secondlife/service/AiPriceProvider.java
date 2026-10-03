package com.secondlife.secondlife.service;

import java.math.BigDecimal;
import java.util.List;

public interface AiPriceProvider {
    PriceSuggestion estimate(String inputSnapshot);
    default PriceSuggestion estimate(String inputSnapshot, List<String> imageUrls) {
        return estimate(inputSnapshot);
    }
    record PriceSuggestion(BigDecimal min, BigDecimal max, BigDecimal suggested,
                           String expectedSellTime, String modelVersion) {}
}
