package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.ReturnShippingFeeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "return_shipping_fees")
@Getter
@Setter
@NoArgsConstructor
public class ReturnShippingFee {
    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private UUID reportId;

    @Column(nullable = false, updatable = false)
    private UUID sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnShippingFeeStatus status = ReturnShippingFeeStatus.PENDING_FEE;

    @Column(precision = 18, scale = 2)
    private BigDecimal feeAmount;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal collectedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal debtAmount = BigDecimal.ZERO;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant assessedAt;
    private UUID assessedBy;
}
