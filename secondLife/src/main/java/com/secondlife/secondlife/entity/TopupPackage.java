package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "topup_packages")
@Getter
@Setter
@NoArgsConstructor
public class TopupPackage {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "post_credits", nullable = false)
    private int postCredits;

    @Column(name = "chat_credits", nullable = false)
    private int chatCredits;

    @Column(name = "valuation_credits", nullable = false)
    private int valuationCredits = 0;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "discount_percentage")
    private double discountPercentage = 0.0;

    @Column(nullable = false)
    private boolean active = true;

    @Version
    @Column(nullable = false)
    private long version;
}
