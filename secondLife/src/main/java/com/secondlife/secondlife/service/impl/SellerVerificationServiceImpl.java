package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationResubmitRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.AdminSellerVerificationDetailResponse;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.dto.risk.SellerRiskResult;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.SellerVerificationEvent;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.ReviewSource;
import com.secondlife.secondlife.enums.RiskStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.enums.VerificationEventType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.SellerVerificationMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.SellerVerificationEventRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.EkycService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.SellerRiskService;
import com.secondlife.secondlife.service.SellerVerificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class SellerVerificationServiceImpl implements SellerVerificationService {

    private static final Set<SellerVerificationStatus> ACTIVE_STATUSES = Set.of(
            SellerVerificationStatus.SUBMITTED,
            SellerVerificationStatus.EKYC_PENDING,
            SellerVerificationStatus.RESUBMIT_REQUIRED,
            SellerVerificationStatus.NEEDS_REVIEW
    );

    private final SellerVerificationRepository sellerVerificationRepository;
    private final SellerVerificationEventRepository sellerVerificationEventRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EkycService ekycService;
    private final SellerRiskService sellerRiskService;
    private final SellerVerificationMapper sellerVerificationMapper;
    private final NotificationService notificationService;
    private final int maxResubmissions;

    public SellerVerificationServiceImpl(
            SellerVerificationRepository sellerVerificationRepository,
            SellerVerificationEventRepository sellerVerificationEventRepository,
            UserRepository userRepository,
            RoleRepository roleRepository,
            EkycService ekycService,
            SellerRiskService sellerRiskService,
            SellerVerificationMapper sellerVerificationMapper,
            NotificationService notificationService,
            @Value("${app.ekyc.max-resubmissions}") int maxResubmissions) {
        this.sellerVerificationRepository = sellerVerificationRepository;
        this.sellerVerificationEventRepository = sellerVerificationEventRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.ekycService = ekycService;
        this.sellerRiskService = sellerRiskService;
        this.sellerVerificationMapper = sellerVerificationMapper;
        this.notificationService = notificationService;
        this.maxResubmissions = maxResubmissions;
    }

    @Override
    @Transactional
    public SellerVerificationResponse submitVerification(UUID userId, SellerVerificationRequest request) {
        User user = userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (user.hasRole(RoleCode.SELLER.name())) {
            throw new ConflictException("Người dùng đã có vai trò SELLER trong hệ thống");
        }

        boolean hasActiveRequest = sellerVerificationRepository.existsByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (hasActiveRequest) {
            throw new ConflictException("Người dùng đã có một yêu cầu xác thực người bán đang được xử lý");
        }

        String rawDocNumber = request.documentNumber().trim();
        String docHash = hashDocumentNumber(rawDocNumber);
        String docMasked = maskDocumentNumber(rawDocNumber);

        SellerVerification verification = new SellerVerification(
                user,
                request.verificationType(),
                rawDocNumber,
                request.documentFrontUrl().trim(),
                request.documentBackUrl().trim(),
                request.selfieUrl() != null ? request.selfieUrl().trim() : null
        );
        verification.setDocumentNumberHash(docHash);
        verification.setDocumentNumberMasked(docMasked);
        verification.setStatus(SellerVerificationStatus.SUBMITTED);

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.SUBMITTED, null, SellerVerificationStatus.SUBMITTED,
                null, "USER", user, "Hồ sơ đăng ký người bán đã được khởi tạo");

        log.info("Seller verification application created for user: {}, id: {}", user.getEmail(), saved.getId());

        // Process through automated eKYC & Risk Decision Pipeline
        return executeDecisionPipeline(saved, user, rawDocNumber, "USER");
    }

    @Override
    @Transactional(readOnly = true)
    public SellerVerificationResponse getCurrentVerification(UUID userId) {
        SellerVerification verification = sellerVerificationRepository.findTopByUserIdOrderBySubmittedAtDesc(userId)
                .orElseThrow(() -> new NotFoundException("Chưa tìm thấy yêu cầu xác thực người bán nào của tài khoản này"));
        return sellerVerificationMapper.toResponse(verification);
    }

    @Override
    @Transactional
    public SellerVerificationResponse resubmitVerification(UUID userId, UUID verificationId, SellerVerificationResubmitRequest request) {
        SellerVerification verification = sellerVerificationRepository.findByIdWithDetails(verificationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ xác thực với id: " + verificationId));

        if (!verification.getUser().getId().equals(userId)) {
            throw new BadRequestException("Hồ sơ xác thực này không thuộc tài khoản hiện tại");
        }

        if (verification.getStatus() != SellerVerificationStatus.RESUBMIT_REQUIRED) {
            throw new ConflictException(String.format(
                    "Chỉ hồ sơ ở trạng thái RESUBMIT_REQUIRED mới được phép gửi lại. Trạng thái hiện tại: %s",
                    verification.getStatus()
            ));
        }

        if (verification.getResubmissionCount() >= maxResubmissions) {
            log.warn("User {} exceeded maximum allowed resubmissions ({}), escalating to NEEDS_REVIEW",
                    userId, maxResubmissions);
            verification.transitionTo(SellerVerificationStatus.NEEDS_REVIEW);
            verification.setReasonCode(ReasonCode.MANUAL_REVIEW_REQUIRED);
            SellerVerification saved = sellerVerificationRepository.save(verification);
            recordEvent(saved, VerificationEventType.RISK_REVIEW_REQUIRED,
                    SellerVerificationStatus.RESUBMIT_REQUIRED, SellerVerificationStatus.NEEDS_REVIEW,
                    ReasonCode.MANUAL_REVIEW_REQUIRED, "SYSTEM", null,
                    "Vượt quá số lần nộp lại tối đa, chuyển sang quản trị viên duyệt thủ công");
            return sellerVerificationMapper.toResponse(saved);
        }

        verification.setDocumentFrontUrl(request.documentFrontUrl().trim());
        verification.setDocumentBackUrl(request.documentBackUrl().trim());
        if (request.selfieUrl() != null && !request.selfieUrl().isBlank()) {
            verification.setSelfieUrl(request.selfieUrl().trim());
        }
        verification.setResubmissionCount(verification.getResubmissionCount() + 1);

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.SUBMITTED,
                SellerVerificationStatus.RESUBMIT_REQUIRED, SellerVerificationStatus.SUBMITTED,
                null, "USER", verification.getUser(),
                "Người dùng đã nộp lại chứng từ lần thứ " + saved.getResubmissionCount());

        log.info("User {} resubmitted verification documents (attempt {})", userId, saved.getResubmissionCount());

        return executeDecisionPipeline(saved, saved.getUser(), saved.getDocumentNumber(), "USER");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SellerVerificationResponse> getAdminVerifications(
            SellerVerificationStatus status,
            EkycStatus ekycStatus,
            RiskStatus riskStatus,
            ReasonCode reasonCode,
            Pageable pageable) {
        Page<SellerVerification> page = sellerVerificationRepository.findWithFilters(
                status, ekycStatus, riskStatus, reasonCode, pageable
        );
        Page<SellerVerificationResponse> dtoPage = page.map(sellerVerificationMapper::toResponse);
        return PageResponse.from(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SellerVerificationResponse> getAdminVerifications(SellerVerificationStatus status, Pageable pageable) {
        return getAdminVerifications(status, null, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSellerVerificationDetailResponse getAdminVerificationById(UUID verificationId) {
        SellerVerification verification = sellerVerificationRepository.findByIdWithDetails(verificationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ xác thực với id: " + verificationId));
        List<SellerVerificationEvent> events = sellerVerificationEventRepository.findByVerificationIdOrderByCreatedAtAsc(verificationId);
        return sellerVerificationMapper.toDetailResponse(verification, events);
    }

    @Override
    @Transactional
    public SellerVerificationResponse approveVerification(UUID adminId, UUID verificationId) {
        SellerVerification verification = sellerVerificationRepository.findByIdWithDetails(verificationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ xác thực với id: " + verificationId));

        if (verification.getStatus() != SellerVerificationStatus.NEEDS_REVIEW) {
            throw new ConflictException(String.format(
                    "Chỉ hồ sơ ở trạng thái NEEDS_REVIEW mới có thể phê duyệt bởi Quản trị viên. Trạng thái hiện tại: %s",
                    verification.getStatus()
            ));
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin Quản trị viên với id: " + adminId));

        User targetUser = verification.getUser();

        // Transition atomically to APPROVED
        SellerVerificationStatus fromStatus = verification.getStatus();
        verification.transitionTo(SellerVerificationStatus.APPROVED);
        verification.setReviewSource(ReviewSource.ADMIN);
        verification.setReviewedBy(admin);
        verification.setReviewedAt(Instant.now());
        verification.setRejectionReason(null);

        grantSellerRole(targetUser);

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.ADMIN_APPROVED, fromStatus, SellerVerificationStatus.APPROVED,
                verification.getReasonCode(), "ADMIN", admin, "Quản trị viên đã phê duyệt hồ sơ người bán");

        log.info("Admin {} explicitly APPROVED seller verification {} for user {}",
                admin.getEmail(), verificationId, targetUser.getEmail());

        notificationService.sendSellerVerificationApproved(targetUser);

        return sellerVerificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SellerVerificationResponse rejectVerification(UUID adminId, UUID verificationId, SellerVerificationReviewRequest request) {
        SellerVerification verification = sellerVerificationRepository.findByIdWithDetails(verificationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ xác thực với id: " + verificationId));

        if (verification.getStatus() != SellerVerificationStatus.NEEDS_REVIEW) {
            throw new ConflictException(String.format(
                    "Chỉ hồ sơ ở trạng thái NEEDS_REVIEW mới có thể từ chối bởi Quản trị viên. Trạng thái hiện tại: %s",
                    verification.getStatus()
            ));
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thông tin Quản trị viên với id: " + adminId));

        SellerVerificationStatus fromStatus = verification.getStatus();
        verification.transitionTo(SellerVerificationStatus.REJECTED);
        verification.setReviewSource(ReviewSource.ADMIN);
        verification.setReviewedBy(admin);
        verification.setReviewedAt(Instant.now());
        verification.setRejectionReason(request.rejectionReason().trim());

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.ADMIN_REJECTED, fromStatus, SellerVerificationStatus.REJECTED,
                verification.getReasonCode(), "ADMIN", admin,
                "Quản trị viên từ chối hồ sơ: " + request.rejectionReason().trim());

        log.info("Admin {} explicitly REJECTED seller verification {} for user {}",
                admin.getEmail(), verificationId, verification.getUser().getEmail());

        notificationService.sendSellerVerificationRejected(verification.getUser(), request.rejectionReason().trim());

        return sellerVerificationMapper.toResponse(saved);
    }

    /**
     * Centralized execution pipeline implementing Decision Table (Cases 1 - 8).
     */
    private SellerVerificationResponse executeDecisionPipeline(
            SellerVerification verification,
            User user,
            String rawDocNumber,
            String actorType) {

        SellerVerificationStatus currentStatus = verification.getStatus();
        verification.transitionTo(SellerVerificationStatus.EKYC_PENDING);
        recordEvent(verification, VerificationEventType.EKYC_STARTED, currentStatus,
                SellerVerificationStatus.EKYC_PENDING, null, "SYSTEM", null, "Bắt đầu thẩm định eKYC tự động");

        // 1. Invoke eKYC Provider Service
        EkycRequest ekycRequest = new EkycRequest(
                verification.getVerificationType(),
                rawDocNumber,
                verification.getDocumentFrontUrl(),
                verification.getDocumentBackUrl(),
                verification.getSelfieUrl()
        );
        EkycResult ekycResult = ekycService.verifyIdentity(ekycRequest);

        verification.setEkycStatus(ekycResult.status());
        verification.setReasonCode(ekycResult.reasonCode());
        verification.setProviderName(ekycResult.providerName());
        verification.setProviderReferenceId(ekycResult.providerReferenceId());
        verification.setFaceMatchScore(ekycResult.faceMatchScore());
        verification.setLivenessScore(ekycResult.livenessScore());
        verification.setDocumentScore(ekycResult.documentScore());
        verification.setEkycCompletedAt(Instant.now());

        // 2. Apply Decision Rules according to Section 24
        switch (ekycResult.status()) {
            case PASSED -> {
                recordEvent(verification, VerificationEventType.EKYC_PASSED,
                        SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.EKYC_PENDING,
                        ekycResult.reasonCode(), "SYSTEM", null, "Kết quả định danh eKYC đạt yêu cầu");

                // Evaluate Seller Risk Rules
                SellerRiskResult riskResult = sellerRiskService.evaluateRisk(user, verification, ekycResult);
                verification.setRiskStatus(riskResult.status());
                verification.setRiskScore(riskResult.riskScore());
                verification.setRiskEvaluatedAt(Instant.now());

                switch (riskResult.status()) {
                    case CLEAR -> {
                        // CASE 1: eKYC PASS + Risk CLEAR => AUTO APPROVE + Grant SELLER role
                        log.info("CASE 1: Auto-approving verification for user: {}", user.getEmail());
                        verification.transitionTo(SellerVerificationStatus.APPROVED);
                        verification.setReviewSource(ReviewSource.SYSTEM);
                        verification.setReviewedAt(Instant.now());
                        verification.setRejectionReason(null);
                        grantSellerRole(user);
                        recordEvent(verification, VerificationEventType.SYSTEM_APPROVED,
                                SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.APPROVED,
                                ReasonCode.NONE, "SYSTEM", null, "Hệ thống tự động phê duyệt hồ sơ người bán");
                        notificationService.sendSellerVerificationApproved(user);
                    }
                    case REVIEW -> {
                        // CASE 2: eKYC PASS + Risk REVIEW => NEEDS_REVIEW
                        log.info("CASE 2: Escalating verification to Admin review for user: {}", user.getEmail());
                        verification.transitionTo(SellerVerificationStatus.NEEDS_REVIEW);
                        ReasonCode primaryReason = !riskResult.reasonCodes().isEmpty()
                                ? riskResult.reasonCodes().get(0)
                                : ReasonCode.MANUAL_REVIEW_REQUIRED;
                        verification.setReasonCode(primaryReason);
                        recordEvent(verification, VerificationEventType.RISK_REVIEW_REQUIRED,
                                SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.NEEDS_REVIEW,
                                primaryReason, "SYSTEM", null, "Hồ sơ cần quản trị viên xem xét do rủi ro: " + riskResult.summary());
                    }
                    case BLOCK -> {
                        // CASE 3: eKYC PASS + Risk BLOCK => AUTO REJECT
                        log.warn("CASE 3: Auto-rejecting verification due to BLOCK risk for user: {}", user.getEmail());
                        verification.transitionTo(SellerVerificationStatus.REJECTED);
                        verification.setReviewSource(ReviewSource.SYSTEM);
                        verification.setReviewedAt(Instant.now());
                        ReasonCode blockReason = !riskResult.reasonCodes().isEmpty()
                                ? riskResult.reasonCodes().get(0)
                                : ReasonCode.RISK_FLAGGED;
                        verification.setReasonCode(blockReason);
                        verification.setRejectionReason(riskResult.summary());
                        recordEvent(verification, VerificationEventType.SYSTEM_REJECTED,
                                SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.REJECTED,
                                blockReason, "SYSTEM", null, "Hệ thống tự động từ chối hồ sơ do vi phạm rủi ro: " + riskResult.summary());
                        notificationService.sendSellerVerificationRejected(user, riskResult.summary());
                    }
                    case NOT_EVALUATED -> {
                        log.error("Unexpected risk evaluation status NOT_EVALUATED after evaluation");
                        verification.transitionTo(SellerVerificationStatus.NEEDS_REVIEW);
                    }
                }
            }
            case FAILED -> {
                // CASE 4: Hard eKYC Failure => AUTO REJECT
                log.warn("CASE 4: eKYC Hard failure for user: {}, reason: {}", user.getEmail(), ekycResult.reasonCode());
                verification.transitionTo(SellerVerificationStatus.REJECTED);
                verification.setReviewSource(ReviewSource.SYSTEM);
                verification.setReviewedAt(Instant.now());
                verification.setRejectionReason(ekycResult.userMessage());
                recordEvent(verification, VerificationEventType.EKYC_FAILED,
                        SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.REJECTED,
                        ekycResult.reasonCode(), "SYSTEM", null, "Xác thực danh tính thất bại: " + ekycResult.userMessage());
                recordEvent(verification, VerificationEventType.SYSTEM_REJECTED,
                        SellerVerificationStatus.REJECTED, SellerVerificationStatus.REJECTED,
                        ekycResult.reasonCode(), "SYSTEM", null, "Hệ thống tự động từ chối do eKYC không đạt");
                notificationService.sendSellerVerificationRejected(user, ekycResult.userMessage());
            }
            case UNCERTAIN -> {
                boolean isUserFixable = ekycResult.reasonCode() != null && ekycResult.reasonCode().isUserFixable();
                if (isUserFixable && verification.getResubmissionCount() < maxResubmissions) {
                    // CASE 5: User-fixable UNCERTAIN => RESUBMIT_REQUIRED
                    log.info("CASE 5: User-fixable eKYC uncertain for user: {}, reason: {}",
                            user.getEmail(), ekycResult.reasonCode());
                    verification.transitionTo(SellerVerificationStatus.RESUBMIT_REQUIRED);
                    verification.setReviewSource(ReviewSource.SYSTEM);
                    verification.setRejectionReason(ekycResult.userMessage());
                    recordEvent(verification, VerificationEventType.RESUBMISSION_REQUIRED,
                            SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.RESUBMIT_REQUIRED,
                            ekycResult.reasonCode(), "SYSTEM", null,
                            "Yêu cầu người dùng nộp lại chứng từ rõ nét hơn: " + ekycResult.userMessage());
                } else {
                    // CASE 6: Non-user-fixable UNCERTAIN or exceeded resubmissions => NEEDS_REVIEW
                    log.info("CASE 6: Uncertain eKYC requiring Admin review for user: {}, reason: {}",
                            user.getEmail(), ekycResult.reasonCode());
                    verification.transitionTo(SellerVerificationStatus.NEEDS_REVIEW);
                    recordEvent(verification, VerificationEventType.RISK_REVIEW_REQUIRED,
                            SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.NEEDS_REVIEW,
                            ekycResult.reasonCode(), "SYSTEM", null,
                            "Hồ sơ định danh ranh giới cần chuyên viên quản trị xem xét thủ công: " + ekycResult.userMessage());
                }
            }
            case PROVIDER_ERROR -> {
                // CASE 7 & 8: Provider Timeout / Unavailable => DO NOT REJECT, keep recoverable in EKYC_PENDING
                log.warn("CASE 7 & 8: eKYC Provider Error for user: {}. Keeping verification in EKYC_PENDING",
                        user.getEmail());
                // Remains in EKYC_PENDING state for future background retry/recovery
                recordEvent(verification, VerificationEventType.EKYC_STARTED,
                        SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.EKYC_PENDING,
                        ekycResult.reasonCode(), "SYSTEM", null,
                        "Cổng eKYC gián đoạn hoặc quá thời gian phản hồi. Giữ trạng thái EKYC_PENDING để tự động phục hồi.");
            }
            case NOT_STARTED, PENDING -> {
                log.debug("Verification state pending/not-started");
            }
        }

        SellerVerification finalSaved = sellerVerificationRepository.save(verification);
        return sellerVerificationMapper.toResponse(finalSaved);
    }

    private void grantSellerRole(User user) {
        Role sellerRole = roleRepository.findByCodeWithPermissions(RoleCode.SELLER.name())
                .orElseThrow(() -> new IllegalStateException("SELLER role not found in database"));

        if (!user.hasRole(RoleCode.SELLER.name())) {
            user.addRole(sellerRole);
            userRepository.save(user);
            log.info("Successfully granted SELLER role to user: {}", user.getEmail());
        }
    }

    private void recordEvent(
            SellerVerification verification,
            VerificationEventType eventType,
            SellerVerificationStatus fromStatus,
            SellerVerificationStatus toStatus,
            ReasonCode reasonCode,
            String actorType,
            User actorUser,
            String notes) {
        SellerVerificationEvent event = new SellerVerificationEvent(
                verification,
                eventType,
                fromStatus,
                toStatus,
                reasonCode,
                actorType,
                actorUser,
                notes
        );
        sellerVerificationEventRepository.save(event);
    }

    private String hashDocumentNumber(String documentNumber) {
        if (documentNumber == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(documentNumber.trim().toUpperCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String maskDocumentNumber(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) return null;
        String trimmed = documentNumber.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(trimmed.length());
        }
        int unmaskedCount = 4;
        int maskedCount = trimmed.length() - unmaskedCount;
        return "*".repeat(maskedCount) + trimmed.substring(maskedCount);
    }
}
