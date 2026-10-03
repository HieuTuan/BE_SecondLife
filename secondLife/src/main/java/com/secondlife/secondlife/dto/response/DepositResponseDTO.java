package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.DepositStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class DepositResponseDTO {
    private UUID id;
    private BigDecimal amount;
    private String code;
    private DepositStatus status;
    private String bankAccountName; 
    private String bankAccountNumber; 
    private String bankName; 
    private Instant createdAt;
}
