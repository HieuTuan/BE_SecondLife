package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.DepositStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DepositResponseDTO(
    UUID id,
    BigDecimal amount,
    String code,
    DepositStatus status,
    String bankAccountName, 
    String bankAccountNumber, 
    String bankName, 
    Instant createdAt
) {
}
