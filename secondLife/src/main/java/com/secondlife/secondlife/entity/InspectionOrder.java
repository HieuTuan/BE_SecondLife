package com.secondlife.secondlife.entity;

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

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, PASSED, FAILED

    @Column(name = "note", columnDefinition = "TEXT")
    private String note; // Ghi chú kết quả kiểm định của Inspector

    @Column(name = "inspection_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal inspectionFee; // Phí kiểm định (lấy từ config lúc tạo)

    @Column(name = "shipping_fee", precision = 18, scale = 2, nullable = false)
    private BigDecimal shippingFee; // Phí vận chuyển đến TT kiểm định (lấy từ config lúc tạo)

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
