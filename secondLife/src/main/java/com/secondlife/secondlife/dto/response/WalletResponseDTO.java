package com.secondlife.secondlife.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class WalletResponseDTO {
    private UUID id;
    private UUID userId;
    private BigDecimal balance;
    private Instant updatedAt;
}
