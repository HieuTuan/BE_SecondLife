package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.PolicyType;
import com.secondlife.secondlife.enums.PolicyVersionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "policy_versions",
        uniqueConstraints = @UniqueConstraint(name = "uq_policy_versions_type_version", columnNames = {"policy_type", "version"})
)
@Getter
@Setter
@NoArgsConstructor
public class PolicyVersion {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 50, updatable = false)
    private PolicyType policyType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "version", nullable = false, length = 50, updatable = false)
    private String version;

    @Column(name = "content_markdown", nullable = false, columnDefinition = "TEXT")
    private String contentMarkdown;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PolicyVersionStatus status;

    @Column(name = "requires_reacceptance", nullable = false)
    private boolean requiresReacceptance = true;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "effective_at")
    private Instant effectiveAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
