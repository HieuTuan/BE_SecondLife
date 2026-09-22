package com.secondlife.secondlife.entity;

import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.ReviewSource;
import com.secondlife.secondlife.enums.RiskStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.enums.VerificationType;
import com.secondlife.secondlife.exception.ConflictException;
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

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "document_number_hash", length = 255)
    private String documentNumberHash;

    @Column(name = "document_number_masked", length = 50)
    private String documentNumberMasked;

    @Column(name = "document_front_url", nullable = false, length = 1024)
    private String documentFrontUrl;

    @Column(name = "document_back_url", nullable = false, length = 1024)
    private String documentBackUrl;

    @Column(name = "selfie_url", length = 1024)
    private String selfieUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SellerVerificationStatus status = SellerVerificationStatus.SUBMITTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "ekyc_status", nullable = false, length = 50)
    private EkycStatus ekycStatus = EkycStatus.NOT_STARTED;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_status", nullable = false, length = 50)
    private RiskStatus riskStatus = RiskStatus.NOT_EVALUATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_source", length = 50)
    private ReviewSource reviewSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", length = 100)
    private ReasonCode reasonCode;

    @Column(name = "provider_name", length = 100)
    private String providerName;

    @Column(name = "provider_reference_id", length = 255)
    private String providerReferenceId;

    @Column(name = "face_match_score")
    private Double faceMatchScore;

    @Column(name = "liveness_score")
    private Double livenessScore;

    @Column(name = "document_score")
    private Double documentScore;

    @Column(name = "risk_score")
    private Double riskScore;

    @Column(name = "resubmission_count", nullable = false)
    private int resubmissionCount = 0;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "ekyc_completed_at")
    private Instant ekycCompletedAt;

    @Column(name = "risk_evaluated_at")
    private Instant riskEvaluatedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public SellerVerification(User user, VerificationType verificationType, String documentNumber,
                              String documentFrontUrl, String documentBackUrl) {
        this(user, verificationType, documentNumber, documentFrontUrl, documentBackUrl, null);
    }

    public SellerVerification(User user, VerificationType verificationType, String documentNumber,
                              String documentFrontUrl, String documentBackUrl, String selfieUrl) {
        this.user = user;
        this.verificationType = verificationType;
        this.documentNumber = documentNumber;
        this.documentFrontUrl = documentFrontUrl;
        this.documentBackUrl = documentBackUrl;
        this.selfieUrl = selfieUrl;
        this.status = SellerVerificationStatus.SUBMITTED;
        this.ekycStatus = EkycStatus.NOT_STARTED;
        this.riskStatus = RiskStatus.NOT_EVALUATED;
        this.resubmissionCount = 0;
    }

    public void transitionTo(SellerVerificationStatus targetStatus) {
        if (!this.status.canTransitionTo(targetStatus)) {
            throw new ConflictException(String.format(
                    "Không thể chuyển trạng thái hồ sơ từ %s sang %s",
                    this.status, targetStatus
            ));
        }
        this.status = targetStatus;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.submittedAt == null) {
            this.submittedAt = now;
        }
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
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
