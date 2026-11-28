package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_wallet_transactions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"inspection_order_id", "inspection_entry"})
})
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class SystemWalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID orderId;
    private UUID inspectionOrderId;
    private UUID topupPackagePurchaseId;
    @Column(length = 32)
    private String inspectionEntry;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal carrierFee = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal unfundedExpense = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SystemWalletTransactionType type;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal heldDelta;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingDelta;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal revenueDelta;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal heldBalanceAfter;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingBalanceAfter;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal revenueBalanceAfter;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
