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

    @Column(name = "request_code", length = 40, unique = true)
    private String requestCode;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "bank_info_confirmed_at")
    private Instant bankInfoConfirmedAt;

    @Column(name = "bank_info_warning_version", length = 40)
    private String bankInfoWarningVersion;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "status", length = 30, nullable = false)
    private String status = "PENDING";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private User acceptedBy;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transferred_by")
    private User transferredBy;

    @Column(name = "transferred_at")
    private Instant transferredAt;

    @Column(name = "notification_at")
    private Instant notificationAt;

    @Column(name = "dispute_deadline")
    private Instant disputeDeadline;
    private Integer reminderHours;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "evidence_url", length = 300)
    private String evidenceUrl;

    @Column(name = "evidence_storage_id", length = 220)
    private String evidenceStorageId;

    @Column(name = "evidence_content_type", length = 40)
    private String evidenceContentType;

    @Column(name = "evidence_uploaded_at")
    private Instant evidenceUploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_uploaded_by")
    private User evidenceUploadedBy;

    @Column(name = "dispute_type", length = 40)
    private String disputeType;

    @Column(name = "dispute_description", columnDefinition = "TEXT")
    private String disputeDescription;

    @Column(name = "disputed_at")
    private Instant disputedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_resolved_by")
    private User disputeResolvedBy;

    @Column(name = "dispute_resolution", columnDefinition = "TEXT")
    private String disputeResolution;

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
        this.status = "PENDING";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
