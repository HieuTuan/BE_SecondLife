package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_return_shipping_policy_history")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionReturnShippingPolicyHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal previousMarkupPercentage;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal newMarkupPercentage;

    @Column(nullable = false)
    private Instant changedAt;

    @Column(nullable = false)
    private UUID changedBy;

    public InspectionReturnShippingPolicyHistory(BigDecimal previousMarkupPercentage, BigDecimal newMarkupPercentage,
                                                  Instant changedAt, UUID changedBy) {
        this.previousMarkupPercentage = previousMarkupPercentage;
        this.newMarkupPercentage = newMarkupPercentage;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }
}
