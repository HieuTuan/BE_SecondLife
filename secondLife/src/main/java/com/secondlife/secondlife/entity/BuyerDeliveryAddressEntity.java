package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "buyer_delivery_addresses")
@Getter
@Setter
@NoArgsConstructor
public class BuyerDeliveryAddressEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "address_json", nullable = false, columnDefinition = "TEXT")
    private String addressJson;

    public BuyerDeliveryAddressEntity(UUID userId, String addressJson) {
        this.userId = userId;
        this.addressJson = addressJson;
    }
}
