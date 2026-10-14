package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.WalletWithdrawal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletWithdrawalResponse {
    private UUID id;
    private UUID userId;
    private String userEmail;
    private BigDecimal amount;
    private String bankCode;
    private String accountNumber;
    private String accountHolderName;
    private String status;
    private UUID reviewedBy;
    private Instant reviewedAt;
    private String adminNote;
    private Instant createdAt;
    private Instant updatedAt;

    public static WalletWithdrawalResponse from(WalletWithdrawal entity) {
        if (entity == null) return null;
        return WalletWithdrawalResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .userEmail(entity.getUser() != null ? entity.getUser().getEmail() : null)
                .amount(entity.getAmount())
                .bankCode(entity.getBankCode())
                .accountNumber(entity.getAccountNumber())
                .accountHolderName(entity.getAccountHolderName())
                .status(entity.getStatus())
                .reviewedBy(entity.getReviewedBy() != null ? entity.getReviewedBy().getId() : null)
                .reviewedAt(entity.getReviewedAt())
                .adminNote(entity.getAdminNote())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
