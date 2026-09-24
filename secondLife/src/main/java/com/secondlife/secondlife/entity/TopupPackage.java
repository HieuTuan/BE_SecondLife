package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
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

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "discount_percentage")
    private double discountPercentage = 0.0;
}
