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
    BigDecimal totalBalance
) {
    public WalletResponseDTO(UUID id, UUID userId, BigDecimal balance, Instant updatedAt) {
        this(id, userId, balance, updatedAt, balance, BigDecimal.ZERO, balance);
    }
}
