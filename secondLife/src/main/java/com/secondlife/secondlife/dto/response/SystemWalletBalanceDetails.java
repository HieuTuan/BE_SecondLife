package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.common.PageResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SystemWalletBalanceDetails(BigDecimal balance, BigDecimal recordsTotal, BigDecimal credited,
                                         BigDecimal debited, Instant updatedAt, PageResponse<Entry> entries) {
    public enum Fund { PRODUCT_ESCROW, SHIPPING }

    public record Entry(UUID orderId, UUID inspectionOrderId, BigDecimal amount, BigDecimal credited, BigDecimal debited) {
    }
}
