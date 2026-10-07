package com.secondlife.secondlife.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="shipments") @Getter @Setter @NoArgsConstructor
public class Shipment {
    @Id private UUID id;
    private UUID orderId;
    private UUID inspectionOrderId;
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
