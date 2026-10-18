package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sepay_transfer_receipts")
@Getter
@Setter
public class SePayTransferReceipt {
    @Id
    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
