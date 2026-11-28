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
@Table(name = "user_policy_acceptance_events")
@Getter
@Setter
@NoArgsConstructor
public class UserPolicyAcceptanceEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_version_id", nullable = false, updatable = false)
    private PolicyVersion policyVersion;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30, updatable = false)
    private PolicyAcceptanceSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "context", nullable = false, length = 30, updatable = false)
    private PolicyAcceptanceContext context;

    @PrePersist
    void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.acceptedAt == null) {
            this.acceptedAt = Instant.now();
        }
    }
}
