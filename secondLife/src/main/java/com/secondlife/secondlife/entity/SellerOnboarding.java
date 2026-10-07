package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seller_onboarding")
@Getter
@Setter
@NoArgsConstructor
public class SellerOnboarding {
    @Id private UUID userId;
    @Column(nullable = false, length = 30) private String shopName;
    @Column(nullable = false, length = 255) private String email;
    @Column(nullable = false, length = 16) private String phone;
    @Column(nullable = false, columnDefinition = "TEXT") private String pickupAddressJson;
    private Integer pickupProvinceId;
    private Integer pickupWardId;
    private Instant emailVerifiedAt;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(length = 255) private String emailOtpHash;
    private Instant emailOtpExpiresAt;
    private Instant emailOtpSentAt;
    @Column(nullable = false) private int emailOtpAttempts;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    @PrePersist
    void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate
    void updated() { updatedAt = Instant.now(); }
}
