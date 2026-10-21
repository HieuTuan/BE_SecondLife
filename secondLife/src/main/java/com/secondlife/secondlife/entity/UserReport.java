package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.dto.request.ReportRequests.ReasonCode;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_reports")
@Getter
@Setter
@NoArgsConstructor
public class UserReport {
    @Id
    private UUID id;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "order_id")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, length = 40)
    private ReasonCode reasonCode;

    @Column(nullable = false, length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_urls", nullable = false, columnDefinition = "jsonb")
    private String evidenceUrls;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsible_party", length = 20)
    private ResponsibleParty responsibleParty;

    @Column(name = "decision_reason", length = 2000)
    private String decisionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "related_post_id")
    private UUID relatedPostId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_action", nullable = false, length = 20)
    private ResolutionAction resolutionAction = ResolutionAction.NONE;

    @Column(name = "return_shipment_id")
    private UUID returnShipmentId;

    @Column(name = "return_approved_at")
    private Instant returnApprovedAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "refunded_by")
    private UUID refundedBy;

    @Column(name = "refund_amount", precision = 18, scale = 2)
    private BigDecimal refundAmount;
}
