package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "order_settlements") @Immutable
@Getter @Setter @NoArgsConstructor
public class OrderSettlement {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true) private UUID orderId;
    @Column(nullable = false) private UUID commissionSnapshotId;
    @Column(nullable = false) private UUID buyerId;
    @Column(nullable = false) private UUID sellerId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal commissionBase;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal rawCommission;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal platformCommission;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal sellerPayout;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal shippingFee;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false, length = 20) private String roundingMode;
    @Column(nullable = false) private Instant settledAt;
}
