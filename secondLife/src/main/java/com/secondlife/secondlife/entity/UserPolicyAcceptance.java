package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.PolicyAcceptanceContext;
import com.secondlife.secondlife.enums.PolicyAcceptanceSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "user_policy_acceptances",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_policy_acceptance", columnNames = {"user_id", "policy_version_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class UserPolicyAcceptance {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_version_id", nullable = false, updatable = false)
    private PolicyVersion policyVersion;

    @Column(name = "first_accepted_at", nullable = false, updatable = false)
    private Instant firstAcceptedAt;

    @Column(name = "last_confirmed_at", nullable = false)
    private Instant lastConfirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private PolicyAcceptanceSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "context", nullable = false, length = 30)
    private PolicyAcceptanceContext context;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.firstAcceptedAt == null) {
            this.firstAcceptedAt = now;
        }
        if (this.lastConfirmedAt == null) {
            this.lastConfirmedAt = this.firstAcceptedAt;
        }
    }
}
