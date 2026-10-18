package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TopupPackageResponse(
    UUID id,
    String name,
    String description,
    int postCredits,
    int chatCredits,
    int valuationCredits,
    BigDecimal price,
    double discountPercentage
) {
}
