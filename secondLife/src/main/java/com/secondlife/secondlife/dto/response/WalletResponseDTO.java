package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponseDTO(
    UUID id,
    UUID userId,
    BigDecimal balance,
    Instant updatedAt
) {
}
