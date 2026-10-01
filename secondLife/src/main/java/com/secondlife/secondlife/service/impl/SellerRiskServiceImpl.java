package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.dto.risk.SellerRiskResult;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.IdentityRestriction;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.IdentityRestrictionRepository;
import com.secondlife.secondlife.service.SellerRiskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Slf4j
@Service
public class SellerRiskServiceImpl implements SellerRiskService {

    private final SellerVerificationRepository sellerVerificationRepository;
    private final IdentityRestrictionRepository identityRestrictionRepository;
    private final boolean duplicateCheckEnabled;
    private final double reviewThreshold;

    public SellerRiskServiceImpl(
            SellerVerificationRepository sellerVerificationRepository,
            IdentityRestrictionRepository identityRestrictionRepository,
            @Value("${app.risk.duplicate-check-enabled}") boolean duplicateCheckEnabled,
            @Value("${app.risk.seller-review-threshold}") double reviewThreshold) {
        this.sellerVerificationRepository = sellerVerificationRepository;
        this.identityRestrictionRepository = identityRestrictionRepository;
        this.duplicateCheckEnabled = duplicateCheckEnabled;
        this.reviewThreshold = reviewThreshold;
    }

    @Override
    public SellerRiskResult evaluateRisk(User user, SellerVerification verification, EkycResult ekycResult) {
        log.info("Evaluating seller risk for userId: {}, verificationId: {}", user.getId(), verification.getId());

        // 1. HARD BLOCK RULES: Account status check
        if (user.getAccountStatus() != null && user.getAccountStatus() != AccountStatus.ACTIVE) {
            ReasonCode blockReason = (user.getAccountStatus() == AccountStatus.LOCKED)
                    ? ReasonCode.ACCOUNT_LOCKED
                    : ReasonCode.ACCOUNT_DISABLED;
            log.warn("Hard BLOCK risk rule triggered for user: {} due to status: {}", user.getId(), user.getAccountStatus());
            return SellerRiskResult.block(List.of(blockReason), "Tài khoản không ở trạng thái hoạt động bình thường");
        }

        Optional<SellerRiskResult> restriction = checkIdentityRestriction(verification);
        if (restriction.isPresent()) {
            return restriction.get();
        }

        List<ReasonCode> reviewReasons = new ArrayList<>();
        double calculatedRiskScore = 0.0;

        // 2. DUPLICATE IDENTITY CHECK
        if (duplicateCheckEnabled && verification.getDocumentNumberHash() != null) {
            List<SellerVerification> matches = sellerVerificationRepository
                    .findByDocumentNumberHash(verification.getDocumentNumberHash());

            boolean duplicateWithAnotherUser = matches.stream()
                    .anyMatch(sv -> !sv.getUser().getId().equals(user.getId())
                            && sv.getStatus() == SellerVerificationStatus.APPROVED);

            if (duplicateWithAnotherUser) {
                log.warn("Duplicate approved identity document detected for user: {}", user.getId());
                reviewReasons.add(ReasonCode.DUPLICATE_IDENTITY);
                calculatedRiskScore = Math.max(calculatedRiskScore, 85.0);
            }
        }

        // 3. REPEATED FAILURES CHECK
        long previousFailures = sellerVerificationRepository.countByUserIdAndEkycStatus(user.getId(), EkycStatus.FAILED);
        if (previousFailures >= 3) {
            log.warn("User {} has {} previous failed eKYC attempts", user.getId(), previousFailures);
            reviewReasons.add(ReasonCode.REPEATED_EKYC_FAILURES);
            calculatedRiskScore = Math.max(calculatedRiskScore, 65.0);
        }

        // 4. BORDERLINE eKYC SCORES CHECK
        if (ekycResult != null && !"VNPT_EKYC".equals(ekycResult.providerName())
                && ekycResult.faceMatchScore() != null && ekycResult.faceMatchScore() < 0.85) {
            reviewReasons.add(ReasonCode.FACE_MATCH_BORDERLINE);
            calculatedRiskScore = Math.max(calculatedRiskScore, 60.0);
        }

        // 5. EVALUATE FINAL OUTCOME
        if (!reviewReasons.isEmpty() || calculatedRiskScore >= reviewThreshold) {
            if (reviewReasons.isEmpty()) {
                reviewReasons.add(ReasonCode.RISK_FLAGGED);
            }
            log.info("Risk evaluation resulted in REVIEW for user: {}, score: {}, reasons: {}",
                    user.getId(), calculatedRiskScore, reviewReasons);
            return SellerRiskResult.review(
                    reviewReasons,
                    calculatedRiskScore,
                    "Hồ sơ cần thẩm định thủ công do có dấu hiệu rủi ro"
            );
        }

        log.info("Risk evaluation CLEAR for user: {}", user.getId());
        return SellerRiskResult.clear();
    }

    @Override
    public Optional<SellerRiskResult> checkIdentityRestriction(SellerVerification verification) {
        String hash = verification.getDocumentNumberHash();
        if (hash == null || hash.isBlank()) {
            return Optional.empty();
        }
        List<IdentityRestriction> active = identityRestrictionRepository.findActiveByDocumentHash(hash, Instant.now());
        if (active.stream().anyMatch(r -> ReasonCode.PERMANENT_SELLER_BAN.name().equals(r.getReasonCode()))) {
            return Optional.of(SellerRiskResult.block(
                    List.of(ReasonCode.PERMANENT_SELLER_BAN), "Danh tính bị hạn chế quyền bán hàng"));
        }
        if (!active.isEmpty()) {
            return Optional.of(SellerRiskResult.review(
                    List.of(ReasonCode.MANUAL_REVIEW_REQUIRED), 0.0,
                    "Danh tính cần kiểm tra hạn chế trước khi phê duyệt"));
        }
        return Optional.empty();
    }
}
