package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_callback_events", uniqueConstraints =
        @UniqueConstraint(columnNames = {"provider", "provider_event_id"}))
@Getter
@Setter
public class PaymentCallbackEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "payment_intent_id", nullable = false)
    private UUID paymentIntentId;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 150)
    private String providerEventId;

    @Column(name = "normalized_status", nullable = false, length = 30)
    private String normalizedStatus;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
