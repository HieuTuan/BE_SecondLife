package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record WalletWithdrawalResponse(
    UUID id,
    UUID userId,
    String userEmail,
    BigDecimal amount,
    String bankCode,
    String accountNumber,
    String accountHolderName,
    String status,
    UUID reviewedBy,
    Instant reviewedAt,
    String adminNote,
    Instant createdAt,
    Instant updatedAt
) {
}
