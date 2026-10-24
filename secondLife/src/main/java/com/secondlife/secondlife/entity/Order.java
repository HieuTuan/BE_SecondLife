package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.enums.OrderPaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private OrderPaymentMethod paymentMethod = OrderPaymentMethod.WALLET;
    private UUID shippingQuoteId;
    @Column(nullable=false, precision=18, scale=2) private BigDecimal shippingFee=BigDecimal.ZERO;
    @Column(columnDefinition="TEXT") private String deliveryAddress;
    private Instant shippingDeliveredAt;
    @Column(name = "shipping_hold", nullable = false)
    private boolean shippingHold;
    private UUID requestId;
    private Instant paidAt;
    private Instant sellerConfirmedAt;
    private Instant confirmationDeadline;
    private Instant confirmationCancelAt;
    @Column(precision=5,scale=2) private BigDecimal confirmationLatePenalty;
    @Column(precision=5,scale=2) private BigDecimal confirmationTimeoutPenalty;
    private Instant handoverDeadline;
    private Instant sellerReadyAt;
    private Instant carrierPickedUpAt;
    private String cancellationReasonCode;
    private UUID cancelledBy;
    private String returnResponsibleParty;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "negotiation_id")
    private Negotiation negotiation;

    @Column(name = "final_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal finalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "escrow_status", nullable = false, length = 20)
    private EscrowStatus escrowStatus = EscrowStatus.HELD;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
