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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shipments")
@Getter
@Setter
@NoArgsConstructor
public class Shipment {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String provider = "LALAMOVE";
    private String driverId;
    @Column(columnDefinition = "TEXT")
    private String shareLink;
    private Instant lastReconciledAt;
    @Column(name = "order_id")
    private UUID orderId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;
    @Column(name = "inspection_order_id")
    private UUID inspectionOrderId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_order_id", insertable = false, updatable = false)
    private InspectionOrder inspectionOrder;
    @Column(name = "report_id")
    private UUID reportId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", insertable = false, updatable = false)
    private UserReport report;
    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", insertable = false, updatable = false)
    private User seller;
    @Column(name = "buyer_id")
    private UUID buyerId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", insertable = false, updatable = false)
    private User buyer;
    @Column(nullable = false)
    private String leg;
    @Column(nullable = false)
    private UUID requestId;
    @Column(nullable = false)
    private String clientOrderCode;
    private String orderCode;
    @Column(nullable = false)
    private String status;
    private String providerStatus;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(columnDefinition = "TEXT")
    private String senderName;
    @Column(columnDefinition = "TEXT")
    private String senderPhone;
    @Column(columnDefinition = "TEXT")
    private String senderAddress;
    @Column(columnDefinition = "TEXT")
    private String receiverName;
    @Column(columnDefinition = "TEXT")
    private String receiverPhone;
    @Column(columnDefinition = "TEXT")
    private String receiverAddress;
    @Column(columnDefinition = "TEXT")
    private String reason;
    private UUID requestedBy;
    @Column(precision = 18, scale = 2)
    private BigDecimal quotedFee;
    @Column(precision = 18, scale = 2)
    private BigDecimal actualFee;
    private Instant expectedDeliveryTime;
    private Instant lastEventAt;
    private Instant lastFeeEventAt;
    private Instant deliveredAt;
    private String podUrl;
    private String lastError;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
