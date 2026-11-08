package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_shipping_quotes")
@Getter
@Setter
@NoArgsConstructor
public class InspectionShippingQuote {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false) private UUID postId;
    @Column(nullable = false) private UUID inspectionCenterId;
    @Column(nullable = false, columnDefinition = "TEXT") private String outboundPayload;
    @Column(nullable = false, columnDefinition = "TEXT") private String returnPayload;
    @Column(nullable = false, columnDefinition = "TEXT") private String outboundResponse;
    @Column(nullable = false, columnDefinition = "TEXT") private String returnResponse;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal outboundFee;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal returnFee;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}
