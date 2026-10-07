package com.secondlife.secondlife.entity;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="shipping_quotes") @Getter @Setter @NoArgsConstructor
public class ShippingQuote {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false) private UUID buyerId;
    @Column(nullable=false) private UUID postId;
    @Column(nullable=false) private String leg="SELLER_TO_BUYER";
    @Column(nullable=false,columnDefinition="TEXT") private String payload;
    @Column(nullable=false,columnDefinition="TEXT") private String deliveryAddress;
    @Column(nullable=false,columnDefinition="TEXT") private String sellerPickupAddress;
    @Column(nullable=false,precision=18,scale=2) private BigDecimal fee;
    private Instant expectedDeliveryTime;
    @Column(nullable=false) private Instant expiresAt;
    private UUID consumedOrderId;
    @Column(nullable=false) private Instant createdAt = Instant.now();
}
