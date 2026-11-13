package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_settlements")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class OrderSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;
    @Column(name = "commission_snapshot_id", nullable = false)
    private UUID commissionSnapshotId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commission_snapshot_id", insertable = false, updatable = false)
    private OrderCommissionSnapshot commissionSnapshot;
    @Column(name = "buyer_id", nullable = false)
    private UUID buyerId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", insertable = false, updatable = false)
    private User buyer;
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", insertable = false, updatable = false)
    private User seller;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal commissionBase;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal rawCommission;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal platformCommission;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal sellerPayout;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingFee;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, length = 20)
    private String roundingMode;
    @Column(nullable = false)
    private Instant settledAt;
}
