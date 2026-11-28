package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AcceptPolicyRequest;
import com.secondlife.secondlife.dto.request.PublishPolicyVersionRequest;
import com.secondlife.secondlife.dto.response.PolicyAcceptanceStatusResponse;
import com.secondlife.secondlife.dto.response.PolicyVersionResponse;
import com.secondlife.secondlife.dto.response.PolicyVersionSummaryResponse;
import com.secondlife.secondlife.entity.PolicyVersion;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserPolicyAcceptance;
import com.secondlife.secondlife.entity.UserPolicyAcceptanceEvent;
import com.secondlife.secondlife.enums.PolicyAcceptanceContext;
import com.secondlife.secondlife.enums.PolicyAcceptanceSource;
import com.secondlife.secondlife.enums.PolicyType;
import com.secondlife.secondlife.enums.PolicyVersionStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.InvalidRequestFieldException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.PolicyVersionRepository;
import com.secondlife.secondlife.repository.UserPolicyAcceptanceEventRepository;
import com.secondlife.secondlife.repository.UserPolicyAcceptanceRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyServiceImpl implements PolicyService {

    private static final String INITIAL_TOS_RESOURCE = "policies/terms-of-service-v1.0.0.md";
    private static final String INITIAL_TOS_VERSION = "1.0.0";
    private static final String INITIAL_TOS_TITLE = "Điều khoản sử dụng và Quy chế hoạt động SecondLife";
    private static final Set<PolicyVersionStatus> PUBLISHED_STATUSES = Set.of(
            PolicyVersionStatus.ACTIVE,
            PolicyVersionStatus.ARCHIVED
    );
    private static final Duration EVENT_RETRY_DEDUP_WINDOW = Duration.ofSeconds(5);

    private final PolicyVersionRepository policyVersionRepository;
    private final UserPolicyAcceptanceRepository acceptanceRepository;
    private final UserPolicyAcceptanceEventRepository eventRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PolicyVersionResponse getCurrentPolicy(PolicyType type) {
        PolicyType effectiveType = type != null ? type : PolicyType.TERMS_OF_SERVICE;
        PolicyVersion active = requireActivePolicy(effectiveType);
        return toResponse(active);
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyVersionResponse getPublishedPolicyVersion(UUID versionId) {
        PolicyVersion version = policyVersionRepository.findByIdAndStatusIn(versionId, PUBLISHED_STATUSES)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phiên bản chính sách đã công bố"));
        return toResponse(version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PolicyVersionSummaryResponse> listPublishedVersions(PolicyType type) {
        PolicyType effectiveType = type != null ? type : PolicyType.TERMS_OF_SERVICE;
        return policyVersionRepository
                .findByPolicyTypeAndStatusInOrderByPublishedAtDesc(effectiveType, PUBLISHED_STATUSES)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyVersion validateExplicitAcceptance(Boolean acceptTerms, UUID policyVersionId, PolicyType type) {
        if (!Boolean.TRUE.equals(acceptTerms)) {
            throw new InvalidRequestFieldException(
                    "acceptTerms",
                    "Bạn phải đọc và xác nhận đồng ý với Điều khoản sử dụng và Quy chế hoạt động hiện hành của SecondLife."
            );
        }
        if (policyVersionId == null) {
            throw new InvalidRequestFieldException(
                    "acceptedPolicyVersionId",
                    "Thiếu mã phiên bản chính sách cần xác nhận. Vui lòng tải lại trang và thử lại."
            );
        }
        PolicyType effectiveType = type != null ? type : PolicyType.TERMS_OF_SERVICE;
        PolicyVersion active = requireActivePolicy(effectiveType);
        if (!active.getId().equals(policyVersionId)) {
            throw new ConflictException(
                    "Chính sách đã được cập nhật lên phiên bản mới (" + active.getVersion()
                            + "). Vui lòng tải lại nội dung và xác nhận phiên bản hiện hành."
            );
        }
        return active;
    }

    @Override
    @Transactional
    public void recordAcceptance(
            User user,
            PolicyVersion version,
            PolicyAcceptanceSource source,
            PolicyAcceptanceContext context
    ) {
        if (user == null || user.getId() == null || version == null || version.getId() == null) {
            throw new IllegalArgumentException("User and policy version are required to record policy acceptance");
        }
        Instant now = Instant.now();
        PolicyAcceptanceSource safeSource = source != null ? source : PolicyAcceptanceSource.API;
        PolicyAcceptanceContext safeContext = context != null ? context : PolicyAcceptanceContext.LOGIN;

        UserPolicyAcceptance existing = acceptanceRepository
                .findByUserIdAndPolicyVersionId(user.getId(), version.getId())
                .orElse(null);

        if (existing != null) {
            existing.setLastConfirmedAt(now);
            existing.setSource(safeSource);
            existing.setContext(safeContext);
            acceptanceRepository.save(existing);
        } else {
            UserPolicyAcceptance acceptance = new UserPolicyAcceptance();
            acceptance.setUser(user);
            acceptance.setPolicyVersion(version);
            acceptance.setFirstAcceptedAt(now);
            acceptance.setLastConfirmedAt(now);
            acceptance.setSource(safeSource);
            acceptance.setContext(safeContext);
            try {
                acceptanceRepository.saveAndFlush(acceptance);
            } catch (DataIntegrityViolationException concurrentEx) {
                UserPolicyAcceptance retryFound = acceptanceRepository
                        .findByUserIdAndPolicyVersionId(user.getId(), version.getId())
                        .orElseThrow(() -> concurrentEx);
                retryFound.setLastConfirmedAt(now);
                retryFound.setSource(safeSource);
                retryFound.setContext(safeContext);
                acceptanceRepository.save(retryFound);
            }
        }

        // Deduplicate rapid retry requests within 5 seconds to prevent duplicate audit rows
        Instant dedupSince = now.minus(EVENT_RETRY_DEDUP_WINDOW);
        boolean recentDuplicate = eventRepository.existsByUserIdAndPolicyVersionIdAndContextAndAcceptedAtAfter(
                user.getId(),
                version.getId(),
                safeContext,
                dedupSince
        );
        if (!recentDuplicate) {
            UserPolicyAcceptanceEvent event = new UserPolicyAcceptanceEvent();
            event.setUser(user);
            event.setPolicyVersion(version);
            event.setAcceptedAt(now);
            event.setSource(safeSource);
            event.setContext(safeContext);
            eventRepository.save(event);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PolicyAcceptanceStatusResponse getAcceptanceStatus(UUID userId, PolicyType type) {
        PolicyType effectiveType = type != null ? type : PolicyType.TERMS_OF_SERVICE;
        PolicyVersion active = requireActivePolicy(effectiveType);

        List<UserPolicyAcceptance> history = acceptanceRepository
                .findHistoryByUserIdAndPolicyType(userId, effectiveType);

        UserPolicyAcceptance currentAcceptance = history.stream()
                .filter(a -> a.getPolicyVersion().getId().equals(active.getId()))
                .findFirst()
                .orElse(null);

        UserPolicyAcceptance latestAcceptance = history.isEmpty() ? null : history.get(0);
        boolean acceptedCurrent = currentAcceptance != null;
        boolean requiresAcceptance = !acceptedCurrent && (active.isRequiresReacceptance() || latestAcceptance == null);

        return new PolicyAcceptanceStatusResponse(
                effectiveType,
                active.getId(),
                active.getVersion(),
                active.getTitle(),
                active.getPublishedAt(),
                active.getEffectiveAt(),
                requiresAcceptance,
                acceptedCurrent,
                currentAcceptance != null ? currentAcceptance.getFirstAcceptedAt() : null,
                currentAcceptance != null ? currentAcceptance.getLastConfirmedAt() : null,
                latestAcceptance != null ? latestAcceptance.getPolicyVersion().getVersion() : null,
                latestAcceptance != null ? latestAcceptance.getFirstAcceptedAt() : null
        );
    }

    @Override
    @Transactional
    public PolicyAcceptanceStatusResponse acceptCurrentPolicy(UUID userId, AcceptPolicyRequest request) {
        PolicyVersion active = validateExplicitAcceptance(
                request.accepted(),
                request.policyVersionId(),
                PolicyType.TERMS_OF_SERVICE
        );
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        PolicyAcceptanceSource source = PolicyAcceptanceSource.fromNullable(request.source());
        PolicyAcceptanceContext context = PolicyAcceptanceContext.fromNullable(
                request.context(),
                PolicyAcceptanceContext.POLICY_UPDATE
        );
        recordAcceptance(user, active, source, context);
        return getAcceptanceStatus(userId, active.getPolicyType());
    }

    @Override
    @Transactional
    public PolicyVersionResponse publishNewVersion(PublishPolicyVersionRequest request) {
        PolicyType type = request.policyType() != null ? request.policyType() : PolicyType.TERMS_OF_SERVICE;
        String normalizedVersion = request.version().trim();
        String normalizedTitle = request.title().trim();
        String content = request.contentMarkdown().trim();

        if (policyVersionRepository.existsByPolicyTypeAndVersion(type, normalizedVersion)) {
            throw new ConflictException("Phiên bản chính sách " + normalizedVersion + " đã tồn tại.");
        }

        Instant now = Instant.now();
        policyVersionRepository.findByPolicyTypeAndStatus(type, PolicyVersionStatus.ACTIVE)
                .ifPresent(existingActive -> {
                    existingActive.setStatus(PolicyVersionStatus.ARCHIVED);
                    existingActive.setUpdatedAt(now);
                    policyVersionRepository.saveAndFlush(existingActive);
                });

        PolicyVersion created = new PolicyVersion();
        created.setPolicyType(type);
        created.setTitle(normalizedTitle);
        created.setVersion(normalizedVersion);
        created.setContentMarkdown(content);
        created.setContentHash(sha256Hex(content));
        created.setStatus(PolicyVersionStatus.ACTIVE);
        created.setRequiresReacceptance(request.requiresReacceptance() == null || request.requiresReacceptance());
        created.setPublishedAt(now);
        created.setEffectiveAt(request.effectiveAt());
        created.setCreatedAt(now);
        created.setUpdatedAt(now);

        PolicyVersion saved = policyVersionRepository.save(created);
        log.info("Published new {} policy version {} (id={})", type, normalizedVersion, saved.getId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void ensureInitialTermsSeeded() {
        if (policyVersionRepository.existsByPolicyType(PolicyType.TERMS_OF_SERVICE)) {
            return;
        }
        String markdown = loadInitialTermsMarkdown();
        Instant now = Instant.now();

        PolicyVersion initial = new PolicyVersion();
        initial.setPolicyType(PolicyType.TERMS_OF_SERVICE);
        initial.setTitle(INITIAL_TOS_TITLE);
        initial.setVersion(INITIAL_TOS_VERSION);
        initial.setContentMarkdown(markdown);
        initial.setContentHash(sha256Hex(markdown));
        initial.setStatus(PolicyVersionStatus.ACTIVE);
        initial.setRequiresReacceptance(true);
        initial.setPublishedAt(now);
        // Never fabricate an unconfirmed effective date; Admins can publish/confirm effectiveAt officially.
        initial.setEffectiveAt(null);
        initial.setCreatedAt(now);
        initial.setUpdatedAt(now);

        try {
            policyVersionRepository.saveAndFlush(initial);
            log.info("Seeded initial TERMS_OF_SERVICE policy version {} (hash={})", INITIAL_TOS_VERSION, initial.getContentHash());
        } catch (DataIntegrityViolationException ex) {
            log.info("Initial TERMS_OF_SERVICE policy already seeded by another instance");
        }
    }

    private PolicyVersion requireActivePolicy(PolicyType type) {
        return policyVersionRepository.findByPolicyTypeAndStatus(type, PolicyVersionStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Chưa có phiên bản chính sách đang áp dụng cho loại " + type));
    }

    private String loadInitialTermsMarkdown() {
        ClassPathResource resource = new ClassPathResource(INITIAL_TOS_RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load bundled Terms of Service resource: " + INITIAL_TOS_RESOURCE, ex);
        }
    }

    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private PolicyVersionResponse toResponse(PolicyVersion v) {
        return new PolicyVersionResponse(
                v.getId(),
                v.getPolicyType(),
                v.getTitle(),
                v.getVersion(),
                v.getContentMarkdown(),
                v.getContentHash(),
                v.getStatus(),
                v.isRequiresReacceptance(),
                v.getPublishedAt(),
                v.getEffectiveAt(),
                v.getUpdatedAt()
        );
    }

    private PolicyVersionSummaryResponse toSummary(PolicyVersion v) {
        return new PolicyVersionSummaryResponse(
                v.getId(),
                v.getPolicyType(),
                v.getTitle(),
                v.getVersion(),
                v.getContentHash(),
                v.getStatus(),
                v.isRequiresReacceptance(),
                v.getPublishedAt(),
                v.getEffectiveAt(),
                v.getUpdatedAt()
        );
    }
}
