package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.VerificationStatus;
import com.secondlife.secondlife.enums.VerificationType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "seller_verifications")
@Getter
@Setter
@NoArgsConstructor
public class SellerVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_type", nullable = false, length = 50)
    private VerificationType verificationType;

    @Column(name = "document_number", nullable = false, length = 100)
    private String documentNumber;

    @Column(name = "document_front_url", nullable = false, length = 1024)
    private String documentFrontUrl;

    @Column(name = "document_back_url", nullable = false, length = 1024)
    private String documentBackUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    public SellerVerification(User user, VerificationType verificationType, String documentNumber,
                              String documentFrontUrl, String documentBackUrl) {
        this.user = user;
        this.verificationType = verificationType;
        this.documentNumber = documentNumber;
        this.documentFrontUrl = documentFrontUrl;
        this.documentBackUrl = documentBackUrl;
        this.status = VerificationStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        this.submittedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SellerVerification that)) return false;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
