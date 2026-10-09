package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.CommissionRuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commission_rules")
@Getter @Setter @NoArgsConstructor
public class CommissionRule {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 150) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private CommissionRuleType type;
    private UUID categoryId;
    @Column(precision = 18, scale = 2) private BigDecimal transactionValueFrom;
    @Column(precision = 18, scale = 2) private BigDecimal transactionValueTo;
    @Column(nullable = false, precision = 9, scale = 6) private BigDecimal rate;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal minCommission;
    @Column(precision = 18, scale = 2) private BigDecimal maxCommission;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private long revision;
    @Column(nullable = false) private UUID updatedBy;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}
