package com.secondlife.secondlife.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
@Entity @Table(name="shipping_pickup_addresses") @Getter @Setter @NoArgsConstructor
public class ShippingPickupAddress {
    @Id private UUID userId;
    @Column(nullable=false, columnDefinition="TEXT") private String addressJson;
}
