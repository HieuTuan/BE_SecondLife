package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.CreditType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credit_balances", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "credit_type"}))
@Getter
@Setter
public class CreditBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false, length = 20)
    private CreditType creditType;

    @Column(nullable = false)
    private long quantity;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
