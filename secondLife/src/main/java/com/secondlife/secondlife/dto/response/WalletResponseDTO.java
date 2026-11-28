package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponseDTO(
    UUID id,
    UUID userId,
    BigDecimal balance,
    Instant updatedAt,
    BigDecimal availableBalance,
    BigDecimal reservedBalance,
    BigDecimal totalBalance,
    String status
) {
    public WalletResponseDTO(UUID id, UUID userId, BigDecimal balance, Instant updatedAt) {
        this(id, userId, balance, updatedAt, balance, BigDecimal.ZERO, balance, "ACTIVE");
    }

    public WalletResponseDTO(UUID id, UUID userId, BigDecimal balance, Instant updatedAt,
                             BigDecimal availableBalance, BigDecimal reservedBalance, BigDecimal totalBalance) {
        this(id, userId, balance, updatedAt, availableBalance, reservedBalance, totalBalance, "ACTIVE");
    }
}
