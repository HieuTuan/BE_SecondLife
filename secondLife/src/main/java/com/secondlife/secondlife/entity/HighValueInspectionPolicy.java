package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "high_value_inspection_policy")
@Getter
@Setter
public class HighValueInspectionPolicy {
    @Id
    private Short id;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal thresholdPrice;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID updatedBy;
}
