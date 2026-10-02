package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.CreditType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_ledger")
@Getter
@Setter
public class CreditLedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false, length = 20)
    private CreditType creditType;

    @Column(name = "purchase_id")
    private UUID purchaseId;

    @Column(name = "entry_type", nullable = false, length = 20)
    private String entryType;

    @Column(name = "quantity_delta", nullable = false)
    private long quantityDelta;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
