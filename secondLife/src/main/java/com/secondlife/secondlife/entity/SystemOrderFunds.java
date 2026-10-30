package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "system_order_funds")
@Getter
@Setter
@NoArgsConstructor
public class SystemOrderFunds {
    @Id
    private UUID orderId;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal productHeld;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingHeld;
}
