package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reputation_incidents")
@Getter
@Setter
@NoArgsConstructor
public class ReputationIncident {

    @Id
    @Column(name = "incident_key", nullable = false, length = 150)
    private String incidentKey;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", insertable = false, updatable = false)
    private User seller;

    @Column(name = "source_report_id", nullable = false)
    private UUID sourceReportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_report_id", insertable = false, updatable = false)
    private UserReport sourceReport;

    @Column(name = "serious", nullable = false)
    private boolean serious;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}

