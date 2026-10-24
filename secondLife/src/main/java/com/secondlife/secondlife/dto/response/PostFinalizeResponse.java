package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;

public record PostFinalizeResponse(
    String title,
    String description,
    BigDecimal suggestedPrice
) {
    public PostFinalizeResponse(String description, BigDecimal suggestedPrice) {
        this(null, description, suggestedPrice);
    }
}

