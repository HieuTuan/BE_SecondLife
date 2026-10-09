package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.CreditPurchaseStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_purchases")
@Getter
@Setter
public class CreditPurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "listing_quantity", nullable = false)
    private int listingQuantity;

    @Column(name = "valuation_quantity", nullable = false)
    private int valuationQuantity;

    @Column(name = "listing_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal listingUnitPrice;

    @Column(name = "valuation_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal valuationUnitPrice;

    @Column(name = "ai_chat_quantity", nullable = false)
    private int aiChatQuantity = 0;

    @Column(name = "ai_chat_unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal aiChatUnitPrice = BigDecimal.ZERO;

    // Legacy snapshot columns are retained for old purchases; new purchases have no discount.
    @Column(name = "discount_tier_id")
    private UUID discountTierId;

    @Column(name = "discount_min_quantity", nullable = false)
    private int discountMinQuantity;

    @Column(name = "discount_max_quantity")
    private Integer discountMaxQuantity;

    @Column(name = "discount_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal discountRate = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "final_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalFee;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditPurchaseStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;
}
