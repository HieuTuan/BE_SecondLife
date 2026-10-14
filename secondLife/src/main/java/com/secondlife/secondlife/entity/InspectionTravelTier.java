package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_travel_tiers")
@Getter
@Setter
@NoArgsConstructor
public class InspectionTravelTier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tier_number", nullable = false, unique = true)
    private Integer tierNumber;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "from_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal fromKm;

    @Column(name = "to_km", precision = 8, scale = 2)
    private BigDecimal toKm;

    @Column(name = "price_per_km", nullable = false, precision = 18, scale = 2)
    private BigDecimal pricePerKm;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
