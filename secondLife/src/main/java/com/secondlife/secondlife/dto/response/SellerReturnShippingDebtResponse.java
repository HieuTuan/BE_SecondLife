package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record SellerReturnShippingDebtResponse(UUID sellerId, BigDecimal outstandingDebt) {
}
