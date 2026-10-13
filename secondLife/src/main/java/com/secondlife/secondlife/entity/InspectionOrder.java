package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_orders")
@Getter
@Setter
@NoArgsConstructor
public class InspectionOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspector_id")
    private User inspector; // Được phân công sau bởi Manager

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 30)
    private InspectionOrderType orderType = InspectionOrderType.AT_CENTER;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_payer", length = 30)
    private InspectionFeePayer feePayer = InspectionFeePayer.BUYER_PAYS;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING"; // WAITING_FEE_PAYMENT, CONFIRMED_PENDING_DISPATCH, DISPATCHED, SCHEDULED, SHIPPING_TO_CENTER, INSPECTING, PASSED, FAILED, CANCELLED, PAYMENT_EXPIRED

    @Column(name = "note", columnDefinition = "TEXT")
    private String note; // Ghi chú kết quả kiểm định của Inspector

    @Column(name = "inspection_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal inspectionFee = BigDecimal.ZERO; // Phí kiểm định cơ sở

    @Column(name = "shipping_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal shippingFee = BigDecimal.ZERO; // Phí vận chuyển chiều đi

    @Column(name = "buyer_fee_share", precision = 18, scale = 2)
    private BigDecimal buyerFeeShare = BigDecimal.ZERO;

    @Column(name = "seller_fee_share", precision = 18, scale = 2)
    private BigDecimal sellerFeeShare = BigDecimal.ZERO;

    @Column(name = "travel_distance_km", precision = 8, scale = 2)
    private BigDecimal travelDistanceKm = BigDecimal.ZERO;

    @Column(name = "travel_fee", precision = 18, scale = 2)
    private BigDecimal travelFee = BigDecimal.ZERO;

    @Column(name = "return_shipping_reserve_fee", precision = 18, scale = 2)
    private BigDecimal returnShippingReserveFee = BigDecimal.ZERO;

    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Column(name = "buyer_paid_at")
    private Instant buyerPaidAt;

    @Column(name = "seller_paid_at")
    private Instant sellerPaidAt;

    @Column(name = "appointment_scheduled_at")
    private Instant appointmentScheduledAt;

    @Column(name = "checklist_result_json", columnDefinition = "TEXT")
    private String checklistResultJson;

    @Column(name = "video_proof_url", columnDefinition = "TEXT")
    private String videoProofUrl;

    @Column(name = "unboxing_video_url", columnDefinition = "TEXT")
    private String unboxingVideoUrl;

    @Column(name = "buyer_consent_zin")
    private boolean buyerConsentZin = false;

    @Column(name = "seller_consent_zin")
    private boolean sellerConsentZin = false;

    @Column(name = "final_payment_deadline")
    private Instant finalPaymentDeadline;

    @Column(name = "final_payment_paid_at")
    private Instant finalPaymentPaidAt;

    @Column(name = "final_item_price", precision = 18, scale = 2)
    private BigDecimal finalItemPrice;

    @Column(name = "final_shipping_fee", precision = 18, scale = 2)
    private BigDecimal finalShippingFee;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
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
