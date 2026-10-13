package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.SystemWallet;
import java.math.BigDecimal;
import java.time.Instant;

public record SystemWalletResponse(String id, String currency, BigDecimal heldBalance, BigDecimal shippingBalance,
                                   BigDecimal revenueBalance, BigDecimal totalBalance, Instant updatedAt) {
    public static SystemWalletResponse from(SystemWallet w) {
        return new SystemWalletResponse(w.getId(), w.getCurrency(), w.getHeldBalance(), w.getShippingBalance(),
                w.getRevenueBalance(), w.getHeldBalance().add(w.getShippingBalance()).add(w.getRevenueBalance()), w.getUpdatedAt());
    }
}
