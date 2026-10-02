package com.secondlife.secondlife.dto.credit;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditDiscountTierResponse(UUID id, int minQuantity, Integer maxQuantity,
                                         BigDecimal discountRate, boolean active) {
}
