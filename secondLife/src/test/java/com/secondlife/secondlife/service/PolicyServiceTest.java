package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AcceptPolicyRequest;
import com.secondlife.secondlife.dto.request.PublishPolicyVersionRequest;
import com.secondlife.secondlife.dto.response.PolicyAcceptanceStatusResponse;
import com.secondlife.secondlife.dto.response.PolicyVersionResponse;
import com.secondlife.secondlife.entity.PolicyVersion;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserPolicyAcceptance;
import com.secondlife.secondlife.entity.UserPolicyAcceptanceEvent;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.PolicyAcceptanceContext;
import com.secondlife.secondlife.enums.PolicyAcceptanceSource;
import com.secondlife.secondlife.enums.PolicyType;
import com.secondlife.secondlife.enums.PolicyVersionStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.InvalidRequestFieldException;
import com.secondlife.secondlife.repository.PolicyVersionRepository;
import com.secondlife.secondlife.repository.UserPolicyAcceptanceEventRepository;
import com.secondlife.secondlife.repository.UserPolicyAcceptanceRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.PolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock private PolicyVersionRepository policyVersionRepository;
    @Mock private UserPolicyAcceptanceRepository acceptanceRepository;
    @Mock private UserPolicyAcceptanceEventRepository eventRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PolicyServiceImpl policyService;

    private PolicyVersion activeV1;
    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() throws Exception {
        activeV1 = new PolicyVersion();
        activeV1.setId(UUID.randomUUID());
        activeV1.setPolicyType(PolicyType.TERMS_OF_SERVICE);
        activeV1.setTitle("Điều khoản sử dụng và Quy chế hoạt động SecondLife");
        activeV1.setVersion("1.0.0");
        activeV1.setContentMarkdown("# Điều khoản sử dụng");
        activeV1.setContentHash(PolicyServiceImpl.sha256Hex("# Điều khoản sử dụng"));
        activeV1.setStatus(PolicyVersionStatus.ACTIVE);
        activeV1.setRequiresReacceptance(true);
        activeV1.setPublishedAt(Instant.now());

        userId = UUID.randomUUID();
        user = new User("buyer@secondlife.vn", "hash", AccountStatus.ACTIVE);
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);
    }

    @Test
    void validateExplicitAcceptance_WhenNotAccepted_ShouldThrow400FieldError() {
        InvalidRequestFieldException ex = assertThrows(
                InvalidRequestFieldException.class,
                () -> policyService.validateExplicitAcceptance(false, activeV1.getId(), PolicyType.TERMS_OF_SERVICE)
        );
        assertEquals("acceptTerms", ex.getField());
    }

    @Test
    void validateExplicitAcceptance_WhenVersionIdMissing_ShouldThrow400FieldError() {
        InvalidRequestFieldException ex = assertThrows(
                InvalidRequestFieldException.class,
                () -> policyService.validateExplicitAcceptance(true, null, PolicyType.TERMS_OF_SERVICE)
        );
        assertEquals("acceptedPolicyVersionId", ex.getField());
    }

    @Test
    void validateExplicitAcceptance_WhenVersionOutdated_ShouldThrow409Conflict() {
        when(policyVersionRepository.findByPolicyTypeAndStatus(PolicyType.TERMS_OF_SERVICE, PolicyVersionStatus.ACTIVE))
                .thenReturn(Optional.of(activeV1));

        UUID outdatedId = UUID.randomUUID();
        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> policyService.validateExplicitAcceptance(true, outdatedId, PolicyType.TERMS_OF_SERVICE)
        );
        assertTrue(ex.getMessage().contains("1.0.0"));
    }

    @Test
    void validateExplicitAcceptance_WhenValid_ShouldReturnActivePolicy() {
        when(policyVersionRepository.findByPolicyTypeAndStatus(PolicyType.TERMS_OF_SERVICE, PolicyVersionStatus.ACTIVE))
                .thenReturn(Optional.of(activeV1));

        PolicyVersion result = policyService.validateExplicitAcceptance(true, activeV1.getId(), PolicyType.TERMS_OF_SERVICE);
        assertEquals(activeV1.getId(), result.getId());
    }

    @Test
    void recordAcceptance_WhenRetriedWithinDedupWindow_ShouldBeIdempotent() {
        UserPolicyAcceptance existing = new UserPolicyAcceptance();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setPolicyVersion(activeV1);
        Instant firstTime = Instant.now().minusSeconds(60);
        existing.setFirstAcceptedAt(firstTime);
        existing.setLastConfirmedAt(firstTime);

        when(acceptanceRepository.findByUserIdAndPolicyVersionId(userId, activeV1.getId()))
                .thenReturn(Optional.of(existing));
        when(eventRepository.existsByUserIdAndPolicyVersionIdAndContextAndAcceptedAtAfter(
                eq(userId), eq(activeV1.getId()), eq(PolicyAcceptanceContext.LOGIN), any(Instant.class)))
                .thenReturn(true);

        policyService.recordAcceptance(user, activeV1, PolicyAcceptanceSource.WEB, PolicyAcceptanceContext.LOGIN);

        assertEquals(firstTime, existing.getFirstAcceptedAt());
        assertTrue(existing.getLastConfirmedAt().isAfter(firstTime));
        verify(acceptanceRepository).save(existing);
        verify(eventRepository, never()).save(any(UserPolicyAcceptanceEvent.class));
    }

    @Test
    void getAcceptanceStatus_WhenOldUserNeverAccepted_ShouldRequireAcceptance() {
        when(policyVersionRepository.findByPolicyTypeAndStatus(PolicyType.TERMS_OF_SERVICE, PolicyVersionStatus.ACTIVE))
                .thenReturn(Optional.of(activeV1));
        when(acceptanceRepository.findHistoryByUserIdAndPolicyType(userId, PolicyType.TERMS_OF_SERVICE))
                .thenReturn(List.of());

        PolicyAcceptanceStatusResponse status = policyService.getAcceptanceStatus(userId, PolicyType.TERMS_OF_SERVICE);

        assertTrue(status.requiresAcceptance());
        assertFalse(status.acceptedCurrentVersion());
        assertNull(status.lastAcceptedVersion());
        assertEquals(activeV1.getId(), status.activePolicyVersionId());
    }

    @Test
    void getAcceptanceStatus_WhenAcceptedOldVersionOnly_ShouldRequireReacceptance() {
        PolicyVersion archivedV0 = new PolicyVersion();
        archivedV0.setId(UUID.randomUUID());
        archivedV0.setPolicyType(PolicyType.TERMS_OF_SERVICE);
        archivedV0.setVersion("0.9.0");
        archivedV0.setStatus(PolicyVersionStatus.ARCHIVED);

        UserPolicyAcceptance oldAcc = new UserPolicyAcceptance();
        oldAcc.setUser(user);
        oldAcc.setPolicyVersion(archivedV0);
        oldAcc.setFirstAcceptedAt(Instant.now().minusSeconds(3600));

        when(policyVersionRepository.findByPolicyTypeAndStatus(PolicyType.TERMS_OF_SERVICE, PolicyVersionStatus.ACTIVE))
                .thenReturn(Optional.of(activeV1));
        when(acceptanceRepository.findHistoryByUserIdAndPolicyType(userId, PolicyType.TERMS_OF_SERVICE))
                .thenReturn(List.of(oldAcc));

        PolicyAcceptanceStatusResponse status = policyService.getAcceptanceStatus(userId, PolicyType.TERMS_OF_SERVICE);

        assertTrue(status.requiresAcceptance());
        assertFalse(status.acceptedCurrentVersion());
        assertEquals("0.9.0", status.lastAcceptedVersion());
    }

    @Test
    void acceptCurrentPolicy_WhenValid_ShouldRecordAndReturnAcceptedStatus() {
        when(policyVersionRepository.findByPolicyTypeAndStatus(PolicyType.TERMS_OF_SERVICE, PolicyVersionStatus.ACTIVE))
                .thenReturn(Optional.of(activeV1));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(acceptanceRepository.findByUserIdAndPolicyVersionId(userId, activeV1.getId()))
                .thenReturn(Optional.empty());
        when(eventRepository.existsByUserIdAndPolicyVersionIdAndContextAndAcceptedAtAfter(
                eq(userId), eq(activeV1.getId()), eq(PolicyAcceptanceContext.POLICY_UPDATE), any(Instant.class)))
                .thenReturn(false);

        UserPolicyAcceptance savedAcc = new UserPolicyAcceptance();
        savedAcc.setUser(user);
        savedAcc.setPolicyVersion(activeV1);
        savedAcc.setFirstAcceptedAt(Instant.now());
        savedAcc.setLastConfirmedAt(Instant.now());
        when(acceptanceRepository.findHistoryByUserIdAndPolicyType(userId, PolicyType.TERMS_OF_SERVICE))
                .thenReturn(List.of(savedAcc));

        PolicyAcceptanceStatusResponse status = policyService.acceptCurrentPolicy(
                userId,
                new AcceptPolicyRequest(activeV1.getId(), true, "WEB", "POLICY_UPDATE")
        );

        assertTrue(status.acceptedCurrentVersion());
        assertFalse(status.requiresAcceptance());
        verify(acceptanceRepository).saveAndFlush(any(UserPolicyAcceptance.class));
        verify(eventRepository).save(any(UserPolicyAcceptanceEvent.class));
    }

    @Test
    void publishNewVersion_ShouldArchiveOldVersionAndKeepHistory() {
        when(policyVersionRepository.existsByPolicyTypeAndVersion(PolicyType.TERMS_OF_SERVICE, "2.0.0"))
                .thenReturn(false);
        when(policyVersionRepository.findByPolicyTypeAndStatus(PolicyType.TERMS_OF_SERVICE, PolicyVersionStatus.ACTIVE))
                .thenReturn(Optional.of(activeV1));
        when(policyVersionRepository.save(any(PolicyVersion.class))).thenAnswer(inv -> {
            PolicyVersion p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        PolicyVersionResponse published = policyService.publishNewVersion(new PublishPolicyVersionRequest(
                PolicyType.TERMS_OF_SERVICE,
                "Điều khoản sử dụng v2",
                "2.0.0",
                "# Nội dung phiên bản 2.0.0",
                null,
                true
        ));

        assertEquals(PolicyVersionStatus.ARCHIVED, activeV1.getStatus());
        assertEquals("2.0.0", published.version());
        assertEquals(PolicyVersionStatus.ACTIVE, published.status());
        assertEquals(PolicyServiceImpl.sha256Hex("# Nội dung phiên bản 2.0.0"), published.contentHash());
        verify(policyVersionRepository).saveAndFlush(activeV1);

        ArgumentCaptor<PolicyVersion> captor = ArgumentCaptor.forClass(PolicyVersion.class);
        verify(policyVersionRepository).save(captor.capture());
        assertEquals("2.0.0", captor.getValue().getVersion());
    }

    @Test
    void ensureInitialTermsSeeded_ShouldLoadCleanPublicResourceWithoutInternalAppendix() {
        when(policyVersionRepository.existsByPolicyType(PolicyType.TERMS_OF_SERVICE)).thenReturn(false);
        when(policyVersionRepository.saveAndFlush(any(PolicyVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        policyService.ensureInitialTermsSeeded();

        ArgumentCaptor<PolicyVersion> captor = ArgumentCaptor.forClass(PolicyVersion.class);
        verify(policyVersionRepository).saveAndFlush(captor.capture());
        PolicyVersion seeded = captor.getValue();
        assertEquals("1.0.0", seeded.getVersion());
        assertEquals(PolicyVersionStatus.ACTIVE, seeded.getStatus());
        assertNull(seeded.getEffectiveAt());
        assertTrue(seeded.getContentMarkdown().contains("ĐIỀU 1."));
        assertTrue(seeded.getContentMarkdown().contains("ĐIỀU 10."));
        assertFalse(seeded.getContentMarkdown().contains("POLICY_RELEASE_CHECKLIST"));
        assertFalse(seeded.getContentMarkdown().contains("PolicyModal.tsx"));
        assertFalse(seeded.getContentMarkdown().contains(".env"));
    }
}
