package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reputation_order_outcomes")
@Getter
@Setter
@NoArgsConstructor
public class ReputationOrderOutcome {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", insertable = false, updatable = false)
    private User seller;

    @Column(name = "outcome", nullable = false, length = 4)
    private String outcome;

    @Column(name = "source_report_id")
    private UUID sourceReportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_report_id", insertable = false, updatable = false)
    private UserReport sourceReport;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

