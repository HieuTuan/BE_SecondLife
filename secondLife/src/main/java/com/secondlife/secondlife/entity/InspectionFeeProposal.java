package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_fee_proposals")
@Getter
@Setter
@NoArgsConstructor
public class InspectionFeeProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "base_inspection_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal baseInspectionFee;

    @Column(name = "tiers_json", nullable = false, columnDefinition = "TEXT")
    private String tiersJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposed_by", nullable = false)
    private User proposedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InspectionFeeProposalStatus status = InspectionFeeProposalStatus.PENDING_APPROVAL;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}
