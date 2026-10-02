package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.enums.VerificationEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "seller_verification_events")
@Getter
@Setter
@NoArgsConstructor
public class SellerVerificationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false)
    private SellerVerification verification;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private VerificationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    private SellerVerificationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    private SellerVerificationStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 100)
    private ReasonCode reasonCode;

    @Column(name = "actor_type", nullable = false, length = 50)
    private String actorType; // "SYSTEM", "ADMIN", "USER"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SellerVerificationEvent(SellerVerification verification,
                                   VerificationEventType eventType,
                                   SellerVerificationStatus fromStatus,
                                   SellerVerificationStatus toStatus,
                                   ReasonCode reasonCode,
                                   String actorType,
                                   User actorUser,
                                   String notes) {
        this.verification = verification;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reasonCode = reasonCode;
        this.actorType = actorType;
        this.actorUser = actorUser;
        this.notes = notes;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SellerVerificationEvent that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
