package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_wallet_transactions", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "type"}))
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class SystemWalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private UUID orderId;
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
