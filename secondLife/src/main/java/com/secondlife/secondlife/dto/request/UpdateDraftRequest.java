package com.secondlife.secondlife.dto.request;

import java.math.BigDecimal;

public record UpdateDraftRequest(
    String title,
    String description,
    String itemCondition,
    BigDecimal price,
    Integer shippingWeight,
    Integer shippingLength,
    Integer shippingWidth,
    Integer shippingHeight
) {
}
