package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.CommissionRuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "order_commission_snapshots") @Immutable
@Getter @Setter @NoArgsConstructor
public class OrderCommissionSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true) private UUID orderId;
    @Column(nullable = false) private UUID ruleId;
    @Column(nullable = false) private long ruleRevision;
    @Column(nullable = false, length = 150) private String ruleName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private CommissionRuleType ruleType;
    private UUID categoryId;
    @Column(precision = 18, scale = 2) private BigDecimal transactionValueFrom;
    @Column(precision = 18, scale = 2) private BigDecimal transactionValueTo;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal rate;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal minCommission;
    @Column(precision = 18, scale = 2) private BigDecimal maxCommission;
    @Column(nullable = false, length = 30) private String baseType;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal commissionBase;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false) private Instant snapshottedAt;
    private UUID capturedBy;
    @Column(length = 1000) private String reason;
}
