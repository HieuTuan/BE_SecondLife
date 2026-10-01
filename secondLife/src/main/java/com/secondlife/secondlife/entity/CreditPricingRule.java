package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.CreditType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_pricing_rules")
@Getter
@Setter
public class CreditPricingRule {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false, unique = true, length = 20)
    private CreditType creditType;

    @Column(name = "unit_price", precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
