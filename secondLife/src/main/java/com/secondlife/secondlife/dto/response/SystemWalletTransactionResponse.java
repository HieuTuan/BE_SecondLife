package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.SystemWalletTransaction;
import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SystemWalletTransactionResponse(Long id, UUID orderId, SystemWalletTransactionType type,
        BigDecimal heldDelta, BigDecimal shippingDelta, BigDecimal revenueDelta, BigDecimal heldBalanceAfter,
        BigDecimal shippingBalanceAfter, BigDecimal revenueBalanceAfter, Instant createdAt) {
    public static SystemWalletTransactionResponse from(SystemWalletTransaction t) {
        return new SystemWalletTransactionResponse(t.getId(), t.getOrderId(), t.getType(), t.getHeldDelta(),
                t.getShippingDelta(), t.getRevenueDelta(), t.getHeldBalanceAfter(), t.getShippingBalanceAfter(),
                t.getRevenueBalanceAfter(), t.getCreatedAt());
    }
}
