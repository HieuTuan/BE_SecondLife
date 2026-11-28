package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reputation_order_faults")
@IdClass(ReputationOrderFaultId.class)
@Getter
@Setter
@NoArgsConstructor
public class ReputationOrderFault {

    @Id
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Id
    @Column(name = "fault_type", nullable = false, length = 30)
    private String faultType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", insertable = false, updatable = false)
    private User seller;

    @Column(name = "source_report_id")
    private UUID sourceReportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_report_id", insertable = false, updatable = false)
    private UserReport sourceReport;

    @Column(name = "actor_id")
    private UUID actorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", insertable = false, updatable = false)
    private User actor;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

