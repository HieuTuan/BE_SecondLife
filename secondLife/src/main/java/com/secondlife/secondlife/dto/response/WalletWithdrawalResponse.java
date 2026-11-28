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
    Instant updatedAt,
    String requestCode,
    String bankName,
    BigDecimal availableBalance,
    BigDecimal reservedBalance,
    UUID acceptedBy,
    Instant acceptedAt,
    UUID transferredBy,
    Instant transferredAt,
    Instant disputeDeadline,
    Instant completedAt,
    String evidenceUrl,
    String disputeType,
    String disputeDescription,
    String disputeResolution,
    String ownerRole
) {
}
