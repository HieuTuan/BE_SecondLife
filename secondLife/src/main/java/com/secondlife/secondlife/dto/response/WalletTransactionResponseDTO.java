package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.WalletTransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WalletTransactionResponseDTO {
    private UUID id;
    private UUID walletId;
    private BigDecimal amount;
    private WalletTransactionType type;
    private UUID referenceId;
    private Instant createdAt;
}
