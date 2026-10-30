package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "system_wallets")
@Getter
@Setter
@NoArgsConstructor
public class SystemWallet {
    public static final String MAIN = "MAIN";
    @Id
    @Column(length = 20)
    private String id;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal heldBalance;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal shippingBalance;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal revenueBalance;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false)
    private Instant updatedAt;
}
