package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.DepositStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deposit_requests")
@Getter
@Setter
public class DepositRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, unique = true)
    private String code; // e.g. SL123456

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepositStatus status = DepositStatus.PENDING;

    @Column(name = "sepay_transaction_id")
    private Long sepayTransactionId; // to prevent duplicate webhooks

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
