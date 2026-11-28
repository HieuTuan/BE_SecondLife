package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seller_reputation_events")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class SellerReputationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", insertable = false, updatable = false)
    private User seller;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "order_id")
    private UUID orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;

    @Column(name = "report_id")
    private UUID reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", insertable = false, updatable = false)
    private UserReport report;

    @Column(name = "actor_id")
    private UUID actorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", insertable = false, updatable = false)
    private User actor;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Column(name = "good_before", nullable = false)
    private long goodBefore;

    @Column(name = "bad_before", nullable = false)
    private long badBefore;

    @Column(name = "good_after", nullable = false)
    private long goodAfter;

    @Column(name = "bad_after", nullable = false)
    private long badAfter;

    @Column(name = "score_before", precision = 5, scale = 2)
    private BigDecimal scoreBefore;

    @Column(name = "score_after", precision = 5, scale = 2)
    private BigDecimal scoreAfter;

    @Column(name = "prior_good")
    private Long priorGood;

    @Column(name = "prior_total")
    private Long priorTotal;

    @Column(name = "formula_version", nullable = false, length = 50)
    private String formulaVersion = "TRANSACTION_SUCCESS_RATE_V2";

    @Column(name = "penalty_before", nullable = false, precision = 18, scale = 2)
    private BigDecimal penaltyBefore = BigDecimal.ZERO;

    @Column(name = "penalty_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal penaltyAfter = BigDecimal.ZERO;

    @Column(name = "event_sequence", insertable = false, updatable = false)
    private Long eventSequence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

