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

@Entity @Table(name="shipments") @Getter @Setter @NoArgsConstructor
public class Shipment {
    @Id private UUID id;
    @Column(nullable=false) private String provider="LALAMOVE";
    private String driverId;
    @Column(columnDefinition="TEXT") private String shareLink;
    private Instant lastReconciledAt;
    private UUID orderId;
    private UUID inspectionOrderId;
    @Column(name="report_id") private UUID reportId;
    @Column(nullable=false) private UUID sellerId;
    private UUID buyerId;
    @Column(nullable=false) private String leg;
    @Column(nullable=false) private UUID requestId;
    @Column(nullable=false) private String clientOrderCode;
    private String orderCode;
    @Column(nullable=false) private String status;
    private String providerStatus;
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Column(columnDefinition="TEXT") private String reason;
    private UUID requestedBy;
    @Column(precision=18,scale=2) private BigDecimal quotedFee;
    @Column(precision=18,scale=2) private BigDecimal actualFee;
    private Instant expectedDeliveryTime;
    private Instant lastEventAt;
    private Instant lastFeeEventAt;
    private Instant deliveredAt;
    private String podUrl;
    private String lastError;
    @Column(nullable=false) private Instant createdAt=Instant.now();
    @Column(nullable=false) private Instant updatedAt=Instant.now();
}
