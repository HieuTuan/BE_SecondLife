package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "shipping_pickup_addresses")
@Getter
@Setter
@NoArgsConstructor
public class ShippingPickupAddress {
    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String addressJson;
}
