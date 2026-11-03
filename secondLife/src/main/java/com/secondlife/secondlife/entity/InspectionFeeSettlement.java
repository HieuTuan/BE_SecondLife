package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_fee_settlements")
@Getter
@Setter
@NoArgsConstructor
public class InspectionFeeSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspection_order_id", nullable = false, unique = true)
    private InspectionOrder inspectionOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 40)
    private InspectionSettlementOutcome outcome;

    @Column(name = "total_collected", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalCollected = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_recipient_user_id")
    private User centerRecipientUser;

    @Column(name = "center_payout_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal centerPayoutAmount = BigDecimal.ZERO;

    @Column(name = "outbound_shipping_allocated", precision = 18, scale = 2, nullable = false)
    private BigDecimal outboundShippingAllocated = BigDecimal.ZERO;

    @Column(name = "return_reserve_allocated", precision = 18, scale = 2, nullable = false)
    private BigDecimal returnReserveAllocated = BigDecimal.ZERO;

    @Column(name = "buyer_refund_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal buyerRefundAmount = BigDecimal.ZERO;

    @Column(name = "seller_refund_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal sellerRefundAmount = BigDecimal.ZERO;

    @Column(name = "seller_compensation_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal sellerCompensationAmount = BigDecimal.ZERO;

    @Column(name = "platform_retained_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal platformRetainedAmount = BigDecimal.ZERO;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt = Instant.now();
}

