package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_withdrawals")
@Getter
@Setter
@NoArgsConstructor
public class WalletWithdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "bank_code", length = 50, nullable = false)
    private String bankCode;

    @Column(name = "account_number", length = 50, nullable = false)
    private String accountNumber;

    @Column(name = "account_holder_name", length = 150, nullable = false)
    private String accountHolderName;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PENDING_REVIEW"; // PENDING_REVIEW, APPROVED, REJECTED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public WalletWithdrawal(User user, BigDecimal amount, String bankCode, String accountNumber, String accountHolderName) {
        this.user = user;
        this.amount = amount;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountHolderName = accountHolderName;
        this.status = "PENDING_REVIEW";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
