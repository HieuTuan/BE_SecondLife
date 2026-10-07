package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.config.EkycRecoveryProperties;
import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationResubmitRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.AdminSellerVerificationDetailResponse;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.dto.risk.SellerRiskResult;
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
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.SellerVerificationMapper;
import com.secondlife.secondlife.repository.SellerVerificationEventRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.EkycService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.RoleAssignmentService;
import com.secondlife.secondlife.service.SellerRiskService;
import com.secondlife.secondlife.service.SellerVerificationService;
import com.secondlife.secondlife.service.SellerOnboardingService;
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
import java.util.Locale;
import java.util.Optional;
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
    private final RoleAssignmentService roleAssignmentService;
    private final EkycService ekycService;
    private final SellerRiskService sellerRiskService;
    private final SellerVerificationMapper sellerVerificationMapper;
    private final NotificationService notificationService;
    private final EkycRecoveryProperties recoveryProperties;
    private final int maxResubmissions;
    private final SellerOnboardingService onboarding;

    public SellerVerificationServiceImpl(
            SellerVerificationRepository sellerVerificationRepository,
            SellerVerificationEventRepository sellerVerificationEventRepository,
            UserRepository userRepository,
            RoleAssignmentService roleAssignmentService,
            EkycService ekycService,
            SellerRiskService sellerRiskService,
            SellerVerificationMapper sellerVerificationMapper,
            NotificationService notificationService,
            EkycRecoveryProperties recoveryProperties,
            @Value("${app.ekyc.max-resubmissions}") int maxResubmissions,
            SellerOnboardingService onboarding) {
        this.sellerVerificationRepository = sellerVerificationRepository;
        this.sellerVerificationEventRepository = sellerVerificationEventRepository;
        this.userRepository = userRepository;
        this.roleAssignmentService = roleAssignmentService;
        this.ekycService = ekycService;
        this.sellerRiskService = sellerRiskService;
        this.sellerVerificationMapper = sellerVerificationMapper;
        this.notificationService = notificationService;
        this.recoveryProperties = recoveryProperties;
        this.maxResubmissions = maxResubmissions;
        this.onboarding = onboarding;
    }

    @Override
    @Transactional
    public SellerVerificationResponse submitVerification(UUID userId, SellerVerificationRequest request) {
        User user = userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        if (user.hasRole(RoleCode.SELLER.name())) {
            throw new ConflictException("NgÃ†Â°Ã¡Â»Âi dÃƒÂ¹ng Ã„â€˜ÃƒÂ£ cÃƒÂ³ vai trÃƒÂ² SELLER trong hÃ¡Â»â€¡ thÃ¡Â»â€˜ng");
        }

        onboarding.requireCompleted(userId);

        boolean hasActiveRequest = sellerVerificationRepository.existsByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (hasActiveRequest) {
            throw new ConflictException("NgÃ†Â°Ã¡Â»Âi dÃƒÂ¹ng Ã„â€˜ÃƒÂ£ cÃƒÂ³ mÃ¡Â»â„¢t yÃƒÂªu cÃ¡ÂºÂ§u xÃƒÂ¡c thÃ¡Â»Â±c ngÃ†Â°Ã¡Â»Âi bÃƒÂ¡n Ã„â€˜ang Ã„â€˜Ã†Â°Ã¡Â»Â£c xÃ¡Â»Â­ lÃƒÂ½");
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
        verification.setVnptClientSession(request.clientSession());
        verification.setVnptRequestToken(request.token());
        verification.setStatus(SellerVerificationStatus.SUBMITTED);

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.SUBMITTED, null, SellerVerificationStatus.SUBMITTED,
                null, "USER", user, "HÃ¡Â»â€œ sÃ†Â¡ Ã„â€˜Ã„Æ’ng kÃƒÂ½ ngÃ†Â°Ã¡Â»Âi bÃƒÂ¡n Ã„â€˜ÃƒÂ£ Ã„â€˜Ã†Â°Ã¡Â»Â£c khÃ¡Â»Å¸i tÃ¡ÂºÂ¡o");

        log.info("Seller verification application created for user ID: {}, id: {}", user.getId(), saved.getId());

        // Process through automated eKYC & Risk Decision Pipeline
        return executeDecisionPipeline(saved, user, rawDocNumber, "USER");
    }

        private SellerVerificationResponse mapToSellerVerificationResponse(SellerVerification verification, UUID userId) {
        if (verification == null) return null;
        String shopName = null;
        String shopEmail = null;
        String phone = null;
        String pickupAddressText = null;

        UUID targetUserId = userId != null ? userId : (verification.getUser() != null ? verification.getUser().getId() : null);
        if (targetUserId != null) {
            try {
                var onbResp = onboarding.get(targetUserId);
                if (onbResp != null) {
                    shopName = onbResp.shopName();
                    shopEmail = onbResp.email();
                    phone = onbResp.phone();
                    if (onbResp.pickupAddress() != null) {
                        var pa = onbResp.pickupAddress();
                        StringBuilder sb = new StringBuilder();
                        if (pa.address() != null && !pa.address().isBlank()) sb.append(pa.address().trim());
                        if (pa.wardName() != null && !pa.wardName().isBlank()) {
                            if (!sb.isEmpty()) sb.append(", ");
                            sb.append(pa.wardName().trim());
                        }
                        if (pa.districtName() != null && !pa.districtName().isBlank()) {
                            if (!sb.isEmpty()) sb.append(", ");
                            sb.append(pa.districtName().trim());
                        }
                        if (pa.provinceName() != null && !pa.provinceName().isBlank()) {
                            if (!sb.isEmpty()) sb.append(", ");
                            sb.append(pa.provinceName().trim());
                        }
                        pickupAddressText = sb.toString();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        List<SellerVerificationEvent> events = sellerVerificationEventRepository.findByVerificationIdOrderByCreatedAtAsc(verification.getId());
        return sellerVerificationMapper.toResponse(verification, shopName, shopEmail, phone, pickupAddressText, events);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerVerificationResponse getCurrentVerification(UUID userId) {
        SellerVerification verification = sellerVerificationRepository.findTopByUserIdOrderBySubmittedAtDesc(userId)
                .orElse(null);
        if (verification == null) {
            return null;
        }
        return mapToSellerVerificationResponse(verification, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SellerVerificationResponse> getVerificationHistory(UUID userId) {
        List<SellerVerification> list = sellerVerificationRepository.findAllByUserIdOrderBySubmittedAtDesc(userId);
        return list.stream()
                .map(v -> mapToSellerVerificationResponse(v, userId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SellerVerificationResponse getVerificationById(UUID userId, UUID verificationId) {
        SellerVerification verification = sellerVerificationRepository.findByIdWithDetails(verificationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ xác thực với id: " + verificationId));
        if (verification.getUser() != null && !verification.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Hồ sơ xác thực này không thuộc tài khoản hiện tại");
        }
        return mapToSellerVerificationResponse(verification, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerVerificationResponse getVerificationByStatus(UUID userId, SellerVerificationStatus status) {
        SellerVerification verification = sellerVerificationRepository.findTopByUserIdAndStatusOrderBySubmittedAtDesc(userId, status)
                .orElse(null);
        if (verification == null) {
            return null;
        }
        return mapToSellerVerificationResponse(verification, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSellerVerificationDetailResponse getStaffVerificationByUserId(UUID userId) {
        SellerVerification verification = sellerVerificationRepository.findTopByUserIdOrderBySubmittedAtDesc(userId)
                .orElseThrow(() -> new NotFoundException("Người bán này chưa có hồ sơ xác thực eKYC nào"));
        return getAdminVerificationById(verification.getId());
    }

    @Override
    @Transactional
    public SellerVerificationResponse resubmitVerification(UUID userId, UUID verificationId, SellerVerificationResubmitRequest request) {
        SellerVerification verification = sellerVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new NotFoundException("KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y hÃ¡Â»â€œ sÃ†Â¡ xÃƒÂ¡c thÃ¡Â»Â±c vÃ¡Â»â€ºi id: " + verificationId));

        if (!verification.getUser().getId().equals(userId)) {
            throw new ForbiddenException("HÃ¡Â»â€œ sÃ†Â¡ xÃƒÂ¡c thÃ¡Â»Â±c nÃƒÂ y khÃƒÂ´ng thuÃ¡Â»â„¢c tÃƒÂ i khoÃ¡ÂºÂ£n hiÃ¡Â»â€¡n tÃ¡ÂºÂ¡i");
        }

        onboarding.requireCompleted(userId);

        if (verification.getStatus() != SellerVerificationStatus.RESUBMIT_REQUIRED) {
            throw new ConflictException(String.format(
                    "ChÃ¡Â»â€° hÃ¡Â»â€œ sÃ†Â¡ Ã¡Â»Å¸ trÃ¡ÂºÂ¡ng thÃƒÂ¡i RESUBMIT_REQUIRED mÃ¡Â»â€ºi Ã„â€˜Ã†Â°Ã¡Â»Â£c phÃƒÂ©p gÃ¡Â»Â­i lÃ¡ÂºÂ¡i. TrÃ¡ÂºÂ¡ng thÃƒÂ¡i hiÃ¡Â»â€¡n tÃ¡ÂºÂ¡i: %s",
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
                    "VÃ†Â°Ã¡Â»Â£t quÃƒÂ¡ sÃ¡Â»â€˜ lÃ¡ÂºÂ§n nÃ¡Â»â„¢p lÃ¡ÂºÂ¡i tÃ¡Â»â€˜i Ã„â€˜a, chuyÃ¡Â»Æ’n sang quÃ¡ÂºÂ£n trÃ¡Â»â€¹ viÃƒÂªn duyÃ¡Â»â€¡t thÃ¡Â»Â§ cÃƒÂ´ng");
            return sellerVerificationMapper.toResponse(saved);
        }

        verification.setDocumentFrontUrl(request.documentFrontUrl().trim());
        verification.setDocumentBackUrl(request.documentBackUrl().trim());
        if (request.selfieUrl() != null && !request.selfieUrl().isBlank()) {
            verification.setSelfieUrl(request.selfieUrl().trim());
        }
        if (request.clientSession() != null && !request.clientSession().isBlank()) {
            verification.setVnptClientSession(request.clientSession().trim());
        }
        if (request.token() != null && !request.token().isBlank()) {
            verification.setVnptRequestToken(request.token().trim());
        }
        verification.setResubmissionCount(verification.getResubmissionCount() + 1);

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.SUBMITTED,
                SellerVerificationStatus.RESUBMIT_REQUIRED, SellerVerificationStatus.SUBMITTED,
                null, "USER", verification.getUser(),
                "NgÃ†Â°Ã¡Â»Âi dÃƒÂ¹ng Ã„â€˜ÃƒÂ£ nÃ¡Â»â„¢p lÃ¡ÂºÂ¡i chÃ¡Â»Â©ng tÃ¡Â»Â« lÃ¡ÂºÂ§n thÃ¡Â»Â© " + saved.getResubmissionCount());

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
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y há»“ sÆ¡ xÃ¡c thá»±c vá»›i id: " + verificationId));
        List<SellerVerificationEvent> events = sellerVerificationEventRepository.findByVerificationIdOrderByCreatedAtAsc(verificationId);

        String shopName = null;
        String shopEmail = null;
        String phone = null;
        String pickupAddressText = null;

        if (verification.getUser() != null) {
            UUID targetUserId = verification.getUser().getId();
            try {
                var onbResp = onboarding.get(targetUserId);
                if (onbResp != null) {
                    shopName = onbResp.shopName();
                    shopEmail = onbResp.email();
                    phone = onbResp.phone();
                    if (onbResp.pickupAddress() != null) {
                        var pa = onbResp.pickupAddress();
                        StringBuilder sb = new StringBuilder();
                        if (pa.address() != null && !pa.address().isBlank()) sb.append(pa.address().trim());
                        if (pa.wardName() != null && !pa.wardName().isBlank()) {
                            if (!sb.isEmpty()) sb.append(", ");
                            sb.append(pa.wardName().trim());
                        }
                        if (pa.districtName() != null && !pa.districtName().isBlank()) {
                            if (!sb.isEmpty()) sb.append(", ");
                            sb.append(pa.districtName().trim());
                        }
                        if (pa.provinceName() != null && !pa.provinceName().isBlank()) {
                            if (!sb.isEmpty()) sb.append(", ");
                            sb.append(pa.provinceName().trim());
                        }
                        pickupAddressText = sb.toString();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return sellerVerificationMapper.toDetailResponse(verification, shopName, shopEmail, phone, pickupAddressText, events);
    }

    @Override
    @Transactional
    public SellerVerificationResponse approveVerification(UUID adminId, UUID verificationId) {
        SellerVerification verification = sellerVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y há»“ sÆ¡ xÃ¡c thá»±c vá»›i id: " + verificationId));

        if (verification.getStatus() != SellerVerificationStatus.NEEDS_REVIEW
                && verification.getStatus() != SellerVerificationStatus.EKYC_PENDING
                && verification.getStatus() != SellerVerificationStatus.SUBMITTED) {
            throw new ConflictException(String.format(
                    "Chá»‰ há»“ sÆ¡ á»Ÿ tráº¡ng thÃ¡i chá» duyá»‡t má»›i cÃ³ thá»ƒ phÃª duyá»‡t. Tráº¡ng thÃ¡i hiá»‡n táº¡i: %s",
                    verification.getStatus()
            ));
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin quáº£n trá»‹ viÃªn / nhÃ¢n viÃªn vá»›i id: " + adminId));

        User targetUser = verification.getUser();

        if (sellerRiskService.checkIdentityRestriction(verification).isPresent()) {
            throw new ConflictException("Danh tÃ­nh Ä‘ang bá»‹ háº¡n cháº¿; khÃ´ng thá»ƒ phÃª duyá»‡t há»“ sÆ¡");
        }

        // Transition atomically to APPROVED
        SellerVerificationStatus fromStatus = verification.getStatus();
        verification.transitionTo(SellerVerificationStatus.APPROVED);
        verification.setReviewSource(ReviewSource.ADMIN);
        verification.setReviewedBy(admin);
        verification.setReviewedAt(Instant.now());
        verification.setRejectionReason(null);

        roleAssignmentService.grantRole(targetUser.getId(), RoleCode.SELLER);

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.ADMIN_APPROVED, fromStatus, SellerVerificationStatus.APPROVED,
                verification.getReasonCode(), "ADMIN", admin, "NhÃ¢n viÃªn / Quáº£n trá»‹ viÃªn Ä‘Ã£ phÃª duyá»‡t há»“ sÆ¡ ngÆ°á»i bÃ¡n");

        log.info("Admin/Staff {} explicitly APPROVED seller verification {} for user {}",
                admin.getId(), verificationId, targetUser.getId());

        notificationService.sendSellerVerificationApproved(targetUser);

        return sellerVerificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SellerVerificationResponse rejectVerification(UUID adminId, UUID verificationId, SellerVerificationReviewRequest request) {
        SellerVerification verification = sellerVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y há»“ sÆ¡ xÃ¡c thá»±c vá»›i id: " + verificationId));

        if (verification.getStatus() != SellerVerificationStatus.NEEDS_REVIEW
                && verification.getStatus() != SellerVerificationStatus.EKYC_PENDING
                && verification.getStatus() != SellerVerificationStatus.SUBMITTED) {
            throw new ConflictException(String.format(
                    "Chá»‰ há»“ sÆ¡ á»Ÿ tráº¡ng thÃ¡i chá» duyá»‡t má»›i cÃ³ thá»ƒ tá»« chá»‘i. Tráº¡ng thÃ¡i hiá»‡n táº¡i: %s",
                    verification.getStatus()
            ));
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin quáº£n trá»‹ viÃªn / nhÃ¢n viÃªn vá»›i id: " + adminId));

        SellerVerificationStatus fromStatus = verification.getStatus();
        verification.transitionTo(SellerVerificationStatus.REJECTED);
        verification.setReviewSource(ReviewSource.ADMIN);
        verification.setReviewedBy(admin);
        verification.setReviewedAt(Instant.now());
        verification.setRejectionReason(request.rejectionReason().trim());

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.ADMIN_REJECTED, fromStatus, SellerVerificationStatus.REJECTED,
                verification.getReasonCode(), "ADMIN", admin,
                "NhÃ¢n viÃªn / Quáº£n trá»‹ viÃªn tá»« chá»‘i há»“ sÆ¡: " + request.rejectionReason().trim());

        log.info("Admin/Staff {} explicitly REJECTED seller verification {} for user {}",
                admin.getId(), verificationId, verification.getUser().getId());

        notificationService.sendSellerVerificationRejected(verification.getUser(), request.rejectionReason().trim());

        return sellerVerificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SellerVerificationResponse requestResubmitVerification(UUID reviewerId, UUID verificationId, SellerVerificationReviewRequest request) {
        SellerVerification verification = sellerVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y há»“ sÆ¡ xÃ¡c thá»±c vá»›i id: " + verificationId));

        if (verification.getStatus() != SellerVerificationStatus.NEEDS_REVIEW
                && verification.getStatus() != SellerVerificationStatus.EKYC_PENDING
                && verification.getStatus() != SellerVerificationStatus.SUBMITTED) {
            throw new ConflictException(String.format(
                    "Chá»‰ há»“ sÆ¡ á»Ÿ tráº¡ng thÃ¡i chá» duyá»‡t má»›i cÃ³ thá»ƒ yÃªu cáº§u ná»™p láº¡i. Tráº¡ng thÃ¡i hiá»‡n táº¡i: %s",
                    verification.getStatus()
            ));
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin ngÆ°á»i kiá»ƒm duyá»‡t vá»›i id: " + reviewerId));

        SellerVerificationStatus fromStatus = verification.getStatus();
        verification.transitionTo(SellerVerificationStatus.RESUBMIT_REQUIRED);
        verification.setReviewSource(ReviewSource.ADMIN);
        verification.setReviewedBy(reviewer);
        verification.setReviewedAt(Instant.now());
        verification.setRejectionReason(request.rejectionReason().trim());

        SellerVerification saved = sellerVerificationRepository.save(verification);
        recordEvent(saved, VerificationEventType.RESUBMISSION_REQUIRED, fromStatus, SellerVerificationStatus.RESUBMIT_REQUIRED,
                verification.getReasonCode(), "ADMIN", reviewer,
                "NhÃ¢n viÃªn yÃªu cáº§u ná»™p láº¡i chá»©ng tá»«: " + request.rejectionReason().trim());

        log.info("Reviewer {} requested resubmission for seller verification {} for user {}",
                reviewer.getId(), verificationId, verification.getUser().getId());

        notificationService.sendSellerVerificationRejected(verification.getUser(),
                "YÃªu cáº§u ná»™p láº¡i chá»©ng tá»«: " + request.rejectionReason().trim());

        return sellerVerificationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SellerVerificationResponse retryPendingVerification(UUID adminId, UUID verificationId) {
        SellerVerification verification = sellerVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new NotFoundException("KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y hÃ¡Â»â€œ sÃ†Â¡ xÃƒÂ¡c thÃ¡Â»Â±c vÃ¡Â»â€ºi id: " + verificationId));
        if (verification.getStatus() != SellerVerificationStatus.EKYC_PENDING
                || verification.getEkycStatus() != EkycStatus.PROVIDER_ERROR) {
            throw new ConflictException("ChÃ¡Â»â€° Ã„â€˜Ã†Â°Ã¡Â»Â£c thÃ¡Â»Â­ lÃ¡ÂºÂ¡i hÃ¡Â»â€œ sÃ†Â¡ EKYC_PENDING cÃƒÂ³ lÃ¡Â»â€”i nhÃƒÂ  cung cÃ¡ÂºÂ¥p");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y thÃƒÂ´ng tin QuÃ¡ÂºÂ£n trÃ¡Â»â€¹ viÃƒÂªn vÃ¡Â»â€ºi id: " + adminId));
        recordEvent(verification, VerificationEventType.EKYC_RETRY_REQUESTED,
                SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.EKYC_PENDING,
                verification.getReasonCode(), "ADMIN", admin,
                "QuÃ¡ÂºÂ£n trÃ¡Â»â€¹ viÃƒÂªn yÃƒÂªu cÃ¡ÂºÂ§u thÃ¡Â»Â­ lÃ¡ÂºÂ¡i eKYC sau lÃ¡Â»â€”i nhÃƒÂ  cung cÃ¡ÂºÂ¥p");
        log.info("Admin {} requested eKYC retry for verification {}", adminId, verificationId);
        return executeDecisionPipeline(verification, verification.getUser(), verification.getDocumentNumber(), "ADMIN");
    }

    @Override
    @Transactional
    public SellerVerificationResponse retryPendingVerificationSystem(UUID verificationId) {
        SellerVerification verification = sellerVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new NotFoundException("KhÃƒÂ´ng tÃƒÂ¬m thÃ¡ÂºÂ¥y hÃ¡Â»â€œ sÃ†Â¡ xÃƒÂ¡c thÃ¡Â»Â±c vÃ¡Â»â€ºi id: " + verificationId));
        Instant now = Instant.now();
        if (verification.getStatus() != SellerVerificationStatus.EKYC_PENDING
                || verification.getEkycStatus() != EkycStatus.PROVIDER_ERROR
                || verification.getNextRetryAt() == null
                || verification.getNextRetryAt().isAfter(now)
                || verification.getRecoveryAttempts() >= recoveryProperties.maxAttempts()) {
            return sellerVerificationMapper.toResponse(verification);
        }
        verification.setRecoveryAttempts(verification.getRecoveryAttempts() + 1);
        verification.setLastRetriedAt(now);
        verification.setNextRetryAt(null);
        recordEvent(verification, VerificationEventType.EKYC_RETRY_REQUESTED,
                SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.EKYC_PENDING,
                verification.getReasonCode(), "SYSTEM", null,
                "HÃ¡Â»â€¡ thÃ¡Â»â€˜ng tÃ¡Â»Â± Ã„â€˜Ã¡Â»â„¢ng thÃ¡Â»Â­ lÃ¡ÂºÂ¡i eKYC sau lÃ¡Â»â€”i nhÃƒÂ  cung cÃ¡ÂºÂ¥p");
        return executeDecisionPipeline(verification, verification.getUser(), verification.getDocumentNumber(), "SYSTEM");
    }

    /**
     * Centralized execution pipeline implementing Decision Table (Cases 1 - 8).
     */
    private SellerVerificationResponse executeDecisionPipeline(
            SellerVerification verification,
            User user,
            String rawDocNumber,
            String actorType) {

        Optional<SellerRiskResult> restriction = sellerRiskService.checkIdentityRestriction(verification);
        if (restriction.isPresent() && restriction.get().status() == RiskStatus.BLOCK) {
            SellerRiskResult result = restriction.get();
            SellerVerificationStatus previousStatus = verification.getStatus();
            verification.transitionTo(SellerVerificationStatus.REJECTED);
            verification.setRiskStatus(RiskStatus.BLOCK);
            verification.setRiskScore(result.riskScore());
            verification.setRiskEvaluatedAt(Instant.now());
            verification.setReasonCode(ReasonCode.PERMANENT_SELLER_BAN);
            verification.setReviewSource(ReviewSource.SYSTEM);
            verification.setReviewedAt(Instant.now());
            verification.setRejectionReason(result.summary());
            verification.setNextRetryAt(null);
            recordEvent(verification, VerificationEventType.SYSTEM_REJECTED, previousStatus,
                    SellerVerificationStatus.REJECTED, ReasonCode.PERMANENT_SELLER_BAN,
                    "SYSTEM", null, "HÃ¡Â»â€œ sÃ†Â¡ bÃ¡Â»â€¹ tÃ¡Â»Â« chÃ¡Â»â€˜i do hÃ¡ÂºÂ¡n chÃ¡ÂºÂ¿ danh tÃƒÂ­nh cÃƒÂ²n hiÃ¡Â»â€¡u lÃ¡Â»Â±c");
            notificationService.sendSellerVerificationRejected(user, result.summary());
            return sellerVerificationMapper.toResponse(sellerVerificationRepository.save(verification));
        }

        // A retry/resubmission must not keep the previous attempt's user-facing error.
        verification.setRejectionReason(null);
        SellerVerificationStatus currentStatus = verification.getStatus();
        verification.transitionTo(SellerVerificationStatus.EKYC_PENDING);
        recordEvent(verification, VerificationEventType.EKYC_STARTED, currentStatus,
                SellerVerificationStatus.EKYC_PENDING, null, "SYSTEM", null, "BÃ¡ÂºÂ¯t Ã„â€˜Ã¡ÂºÂ§u thÃ¡ÂºÂ©m Ã„â€˜Ã¡Â»â€¹nh eKYC tÃ¡Â»Â± Ã„â€˜Ã¡Â»â„¢ng");

        // 1. Invoke eKYC Provider Service
        EkycRequest ekycRequest = new EkycRequest(
                verification.getVerificationType(),
                rawDocNumber,
                verification.getDocumentFrontUrl(),
                verification.getDocumentBackUrl(),
                verification.getSelfieUrl(),
                verification.getVnptClientSession(),
                verification.getVnptRequestToken()
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
        if (ekycResult.status() != EkycStatus.PROVIDER_ERROR) {
            verification.setNextRetryAt(null);
        }

        // 2. Apply Decision Rules according to Section 24
        switch (ekycResult.status()) {
            case PASSED -> {
                recordEvent(verification, VerificationEventType.EKYC_PASSED,
                        SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.EKYC_PENDING,
                        ekycResult.reasonCode(), "SYSTEM", null, "KÃ¡ÂºÂ¿t quÃ¡ÂºÂ£ Ã„â€˜Ã¡Â»â€¹nh danh eKYC Ã„â€˜Ã¡ÂºÂ¡t yÃƒÂªu cÃ¡ÂºÂ§u");

                // Evaluate Seller Risk Rules
                SellerRiskResult riskResult = sellerRiskService.evaluateRisk(user, verification, ekycResult);
                verification.setRiskStatus(riskResult.status());
                verification.setRiskScore(riskResult.riskScore());
                verification.setRiskEvaluatedAt(Instant.now());

                switch (riskResult.status()) {
                    case CLEAR -> {
                        // CASE 1: eKYC PASS + Risk CLEAR => AUTO APPROVE + Grant SELLER role
                        log.info("CASE 1: Auto-approving verification for user ID: {}", user.getId());
                        verification.transitionTo(SellerVerificationStatus.APPROVED);
                        verification.setReviewSource(ReviewSource.SYSTEM);
                        verification.setReviewedAt(Instant.now());
                        verification.setRejectionReason(null);
                        roleAssignmentService.grantRole(user.getId(), RoleCode.SELLER);
                        recordEvent(verification, VerificationEventType.SYSTEM_APPROVED,
                                SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.APPROVED,
                                ReasonCode.NONE, "SYSTEM", null, "HÃ¡Â»â€¡ thÃ¡Â»â€˜ng tÃ¡Â»Â± Ã„â€˜Ã¡Â»â„¢ng phÃƒÂª duyÃ¡Â»â€¡t hÃ¡Â»â€œ sÃ†Â¡ ngÃ†Â°Ã¡Â»Âi bÃƒÂ¡n");
                        notificationService.sendSellerVerificationApproved(user);
                    }
                    case REVIEW -> {
                        // CASE 2: eKYC PASS + Risk REVIEW => NEEDS_REVIEW
                        log.info("CASE 2: Escalating verification to Admin review for user ID: {}", user.getId());
                        verification.transitionTo(SellerVerificationStatus.NEEDS_REVIEW);
                        ReasonCode primaryReason = !riskResult.reasonCodes().isEmpty()
                                ? riskResult.reasonCodes().get(0)
                                : ReasonCode.MANUAL_REVIEW_REQUIRED;
                        verification.setReasonCode(primaryReason);
                        recordEvent(verification, VerificationEventType.RISK_REVIEW_REQUIRED,
                                SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.NEEDS_REVIEW,
                                primaryReason, "SYSTEM", null, "HÃ¡Â»â€œ sÃ†Â¡ cÃ¡ÂºÂ§n quÃ¡ÂºÂ£n trÃ¡Â»â€¹ viÃƒÂªn xem xÃƒÂ©t do rÃ¡Â»Â§i ro: " + riskResult.summary());
                    }
                    case BLOCK -> {
                        // CASE 3: eKYC PASS + Risk BLOCK => AUTO REJECT
                        log.warn("CASE 3: Auto-rejecting verification due to BLOCK risk for user ID: {}", user.getId());
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
                                blockReason, "SYSTEM", null, "HÃ¡Â»â€¡ thÃ¡Â»â€˜ng tÃ¡Â»Â± Ã„â€˜Ã¡Â»â„¢ng tÃ¡Â»Â« chÃ¡Â»â€˜i hÃ¡Â»â€œ sÃ†Â¡ do vi phÃ¡ÂºÂ¡m rÃ¡Â»Â§i ro: " + riskResult.summary());
                        notificationService.sendSellerVerificationRejected(user, riskResult.summary());
                    }
                    case NOT_EVALUATED -> {
                        log.error("Unexpected risk evaluation status NOT_EVALUATED after evaluation");
                        verification.transitionTo(SellerVerificationStatus.NEEDS_REVIEW);
                        verification.setReasonCode(ReasonCode.MANUAL_REVIEW_REQUIRED);
                        recordEvent(verification, VerificationEventType.RISK_REVIEW_REQUIRED,
                                SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.NEEDS_REVIEW,
                                ReasonCode.MANUAL_REVIEW_REQUIRED, "SYSTEM", null,
                                "KhÃƒÂ´ng xÃƒÂ¡c Ã„â€˜Ã¡Â»â€¹nh Ã„â€˜Ã†Â°Ã¡Â»Â£c mÃ¡Â»Â©c rÃ¡Â»Â§i ro; chuyÃ¡Â»Æ’n hÃ¡Â»â€œ sÃ†Â¡ sang thÃ¡ÂºÂ©m Ã„â€˜Ã¡Â»â€¹nh thÃ¡Â»Â§ cÃƒÂ´ng");
                    }
                }
            }
            case FAILED -> {
                // CASE 4: Hard eKYC Failure => AUTO REJECT
                log.warn("CASE 4: eKYC Hard failure for user ID: {}, reason: {}", user.getId(), ekycResult.reasonCode());
                verification.transitionTo(SellerVerificationStatus.REJECTED);
                verification.setReviewSource(ReviewSource.SYSTEM);
                verification.setReviewedAt(Instant.now());
                verification.setRejectionReason(ekycResult.userMessage());
                recordEvent(verification, VerificationEventType.EKYC_FAILED,
                        SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.REJECTED,
                        ekycResult.reasonCode(), "SYSTEM", null, "XÃƒÂ¡c thÃ¡Â»Â±c danh tÃƒÂ­nh thÃ¡ÂºÂ¥t bÃ¡ÂºÂ¡i: " + ekycResult.userMessage());
                recordEvent(verification, VerificationEventType.SYSTEM_REJECTED,
                        SellerVerificationStatus.REJECTED, SellerVerificationStatus.REJECTED,
                        ekycResult.reasonCode(), "SYSTEM", null, "HÃ¡Â»â€¡ thÃ¡Â»â€˜ng tÃ¡Â»Â± Ã„â€˜Ã¡Â»â„¢ng tÃ¡Â»Â« chÃ¡Â»â€˜i do eKYC khÃƒÂ´ng Ã„â€˜Ã¡ÂºÂ¡t");
                notificationService.sendSellerVerificationRejected(user, ekycResult.userMessage());
            }
            case UNCERTAIN -> {
                boolean isUserFixable = ekycResult.reasonCode() != null && ekycResult.reasonCode().isUserFixable();
                if (isUserFixable && verification.getResubmissionCount() < maxResubmissions) {
                    // CASE 5: User-fixable UNCERTAIN => RESUBMIT_REQUIRED
                    log.info("CASE 5: User-fixable eKYC uncertain for user: {}, reason: {}",
                            user.getId(), ekycResult.reasonCode());
                    verification.transitionTo(SellerVerificationStatus.RESUBMIT_REQUIRED);
                    verification.setReviewSource(ReviewSource.SYSTEM);
                    verification.setRejectionReason(ekycResult.userMessage());
                    recordEvent(verification, VerificationEventType.RESUBMISSION_REQUIRED,
                            SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.RESUBMIT_REQUIRED,
                            ekycResult.reasonCode(), "SYSTEM", null,
                            "YÃƒÂªu cÃ¡ÂºÂ§u ngÃ†Â°Ã¡Â»Âi dÃƒÂ¹ng nÃ¡Â»â„¢p lÃ¡ÂºÂ¡i chÃ¡Â»Â©ng tÃ¡Â»Â« rÃƒÂµ nÃƒÂ©t hÃ†Â¡n: " + ekycResult.userMessage());
                } else {
                    // CASE 6: Non-user-fixable UNCERTAIN or exceeded resubmissions => NEEDS_REVIEW
                    log.info("CASE 6: Uncertain eKYC requiring Admin review for user: {}, reason: {}",
                            user.getId(), ekycResult.reasonCode());
                    verification.transitionTo(SellerVerificationStatus.NEEDS_REVIEW);
                    recordEvent(verification, VerificationEventType.RISK_REVIEW_REQUIRED,
                            SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.NEEDS_REVIEW,
                            ekycResult.reasonCode(), "SYSTEM", null,
                            "HÃ¡Â»â€œ sÃ†Â¡ Ã„â€˜Ã¡Â»â€¹nh danh ranh giÃ¡Â»â€ºi cÃ¡ÂºÂ§n chuyÃƒÂªn viÃƒÂªn quÃ¡ÂºÂ£n trÃ¡Â»â€¹ xem xÃƒÂ©t thÃ¡Â»Â§ cÃƒÂ´ng: " + ekycResult.userMessage());
                }
            }
            case PROVIDER_ERROR -> {
                // CASE 7 & 8: Provider Timeout / Unavailable => DO NOT REJECT, keep recoverable in EKYC_PENDING
                log.warn("CASE 7 & 8: eKYC Provider Error for user: {}. Keeping verification in EKYC_PENDING",
                        user.getId());
                boolean isRecoverable = ekycResult.reasonCode() != ReasonCode.PROVIDER_REQUEST_REJECTED
                        && ekycResult.reasonCode() != ReasonCode.PROVIDER_AUTH_FAILED;
                verification.setNextRetryAt(isRecoverable && verification.getRecoveryAttempts() < recoveryProperties.maxAttempts()
                        ? Instant.now().plusMillis(recoveryProperties.delayMs()) : null);
                recordEvent(verification, VerificationEventType.EKYC_STARTED,
                        SellerVerificationStatus.EKYC_PENDING, SellerVerificationStatus.EKYC_PENDING,
                        ekycResult.reasonCode(), "SYSTEM", null,
                        isRecoverable
                                ? "CÃ¡Â»â€¢ng eKYC giÃƒÂ¡n Ã„â€˜oÃ¡ÂºÂ¡n hoÃ¡ÂºÂ·c quÃƒÂ¡ thÃ¡Â»Âi gian phÃ¡ÂºÂ£n hÃ¡Â»â€œi. GiÃ¡Â»Â¯ trÃ¡ÂºÂ¡ng thÃƒÂ¡i EKYC_PENDING Ã„â€˜Ã¡Â»Æ’ tÃ¡Â»Â± Ã„â€˜Ã¡Â»â„¢ng phÃ¡Â»Â¥c hÃ¡Â»â€œi."
                                : "CÃ¡Â»â€¢ng eKYC tÃ¡Â»Â« chÃ¡Â»â€˜i yÃƒÂªu cÃ¡ÂºÂ§u (" + ekycResult.reasonCode() + "). KhÃƒÂ´ng tÃ¡Â»Â± Ã„â€˜Ã¡Â»â„¢ng phÃ¡Â»Â¥c hÃ¡Â»â€œi.");
            }
            case NOT_STARTED, PENDING -> {
                log.debug("Verification state pending/not-started");
            }
        }

        SellerVerification finalSaved = sellerVerificationRepository.save(verification);
        return sellerVerificationMapper.toResponse(finalSaved);
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
        String normalized = normalizeDocumentNumber(documentNumber);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
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
        String normalized = normalizeDocumentNumber(documentNumber);
        if (normalized.length() <= 4) {
            return "*".repeat(normalized.length());
        }
        int unmaskedCount = 4;
        int maskedCount = normalized.length() - unmaskedCount;
        return "*".repeat(maskedCount) + normalized.substring(maskedCount);
    }

    private String normalizeDocumentNumber(String documentNumber) {
        String normalized = documentNumber == null ? ""
                : documentNumber.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new BadRequestException("Document number must contain letters or digits");
        }
        return normalized;
    }
}
