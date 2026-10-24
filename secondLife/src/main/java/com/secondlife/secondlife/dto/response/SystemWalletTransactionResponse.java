package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SystemWalletTransactionResponse(Long id, UUID orderId, SystemWalletTransactionType type,
        BigDecimal heldDelta, BigDecimal shippingDelta, BigDecimal revenueDelta, BigDecimal heldBalanceAfter,
        BigDecimal shippingBalanceAfter, BigDecimal revenueBalanceAfter, Instant createdAt) {

}
