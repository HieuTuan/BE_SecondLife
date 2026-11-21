package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.InspectionOrderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pre_listing_inspection_quotes")
@Getter
@Setter
public class PreListingInspectionQuote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID postId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InspectionOrderType orderType;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String quoteJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String sellerPickupJson;
}
