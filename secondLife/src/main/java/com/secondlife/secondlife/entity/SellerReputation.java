package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seller_reputations")
@Getter
@Setter
@NoArgsConstructor
public class SellerReputation {
    @Id
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "good_transactions", nullable = false)
    private long goodTransactions;

    @Column(name = "bad_transactions", nullable = false)
    private long badTransactions;

    @Column(name = "confirmed_incidents", nullable = false)
    private long confirmedIncidents;

    @Column(name = "serious_incidents", nullable = false)
    private long seriousIncidents;

    @Column(name = "selling_restricted", nullable = false)
    private boolean sellingRestricted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "confirmation_penalty_points", nullable = false, precision = 18, scale = 2)
    private BigDecimal confirmationPenaltyPoints = BigDecimal.ZERO;
}
