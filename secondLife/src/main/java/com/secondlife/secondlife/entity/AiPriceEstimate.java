package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_price_estimates")
@Getter
@Setter
@NoArgsConstructor
public class AiPriceEstimate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "estimate_id")
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "fair_price_min", precision = 18, scale = 2)
    private BigDecimal fairPriceMin;

    @Column(name = "fair_price_max", precision = 18, scale = 2)
    private BigDecimal fairPriceMax;

    @Column(name = "suggested_price", precision = 18, scale = 2)
    private BigDecimal suggestedPrice;

    @Column(name = "expected_sell_time", length = 100)
    private String expectedSellTime;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
