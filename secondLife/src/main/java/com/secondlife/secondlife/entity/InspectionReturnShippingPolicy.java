package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "inspection_return_shipping_policy")
@Getter
@Setter
public class InspectionReturnShippingPolicy {
    @Id
    private Short id;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal markupPercentage;
}
