package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.WalletTransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record WalletTransactionResponseDTO(
    UUID id,
    UUID walletId,
    BigDecimal amount,
    WalletTransactionType type,
    UUID referenceId,
    Instant createdAt
) {
}
