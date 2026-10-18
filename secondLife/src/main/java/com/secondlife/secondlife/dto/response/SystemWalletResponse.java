package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record SystemWalletResponse(String id, String currency, BigDecimal heldBalance, BigDecimal shippingBalance,
                                   BigDecimal revenueBalance, BigDecimal totalBalance, Instant updatedAt) {

}
