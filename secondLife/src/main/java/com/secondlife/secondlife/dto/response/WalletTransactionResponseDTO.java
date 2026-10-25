package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.WalletTransactionDirection;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.enums.WalletTransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(description = "Business record ID; interpret using referenceType, not the transaction type")
    UUID referenceId,
    Instant createdAt,
    @Schema(description = "Wallet currency", example = "VND")
    String currency,
    @Schema(description = "CREDIT adds money, DEBIT removes money, NEUTRAL represents a legacy zero movement")
    WalletTransactionDirection direction,
    @Schema(description = "Vietnamese explanation of the recorded wallet movement")
    String description,
    @Schema(description = "Kind of referenced business record; UNKNOWN for legacy rows without recorded provenance")
    WalletTransactionReferenceType referenceType,
    @Schema(description = "Balance immediately before posting; null for legacy transactions without a snapshot")
    BigDecimal balanceBefore,
    @Schema(description = "Balance immediately after posting; null for legacy transactions without a snapshot")
    BigDecimal balanceAfter
) {
}
