package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.dto.risk.SellerRiskResult;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.service.impl.SellerRiskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerRiskServiceTest {

    @Mock
    private SellerVerificationRepository sellerVerificationRepository;

    private SellerRiskServiceImpl sellerRiskService;

    private User user;
    private UUID userId;
    private SellerVerification verification;

    @BeforeEach
    void setUp() throws Exception {
        sellerRiskService = new SellerRiskServiceImpl(sellerVerificationRepository, true, 70.0);

        userId = UUID.randomUUID();
        user = new User("user@example.com", "pass", AccountStatus.ACTIVE);
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);

        verification = new SellerVerification(user, VerificationType.CITIZEN_ID, "012345678901", "http://f", "http://b");
        verification.setDocumentNumberHash("hashed_1234");
    }

    @Test
    void evaluateRisk_WhenUserActiveAndNoDuplicate_ShouldReturnClear() {
        when(sellerVerificationRepository.findByDocumentNumberHash("hashed_1234")).thenReturn(List.of());
        when(sellerVerificationRepository.countByUserIdAndEkycStatus(userId, EkycStatus.FAILED)).thenReturn(0L);

        EkycResult ekycResult = EkycResult.pass("MOCK", "REF1", 0.95, 0.99, 0.98);
        SellerRiskResult result = sellerRiskService.evaluateRisk(user, verification, ekycResult);

        assertEquals(RiskStatus.CLEAR, result.status());
        assertEquals(0.0, result.riskScore());
    }

    @Test
    void evaluateRisk_WhenUserLocked_ShouldReturnBlock() {
        user.setAccountStatus(AccountStatus.LOCKED);

        EkycResult ekycResult = EkycResult.pass("MOCK", "REF1", 0.95, 0.99, 0.98);
        SellerRiskResult result = sellerRiskService.evaluateRisk(user, verification, ekycResult);

        assertEquals(RiskStatus.BLOCK, result.status());
        assertTrue(result.reasonCodes().contains(ReasonCode.ACCOUNT_LOCKED));
    }

    @Test
    void evaluateRisk_WhenUserDisabled_ShouldReturnBlock() {
        user.setAccountStatus(AccountStatus.DISABLED);

        EkycResult ekycResult = EkycResult.pass("MOCK", "REF1", 0.95, 0.99, 0.98);
        SellerRiskResult result = sellerRiskService.evaluateRisk(user, verification, ekycResult);

        assertEquals(RiskStatus.BLOCK, result.status());
        assertTrue(result.reasonCodes().contains(ReasonCode.ACCOUNT_DISABLED));
    }

    @Test
    void evaluateRisk_WhenDuplicateIdentityWithAnotherApprovedUser_ShouldReturnReview() throws Exception {
        User anotherUser = new User("other@example.com", "pass", AccountStatus.ACTIVE);
        UUID otherId = UUID.randomUUID();
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(anotherUser, otherId);

        SellerVerification approvedOther = new SellerVerification(anotherUser, VerificationType.CITIZEN_ID, "012345678901", "http://f", "http://b");
        approvedOther.setStatus(SellerVerificationStatus.APPROVED);

        when(sellerVerificationRepository.findByDocumentNumberHash("hashed_1234")).thenReturn(List.of(approvedOther));
        when(sellerVerificationRepository.countByUserIdAndEkycStatus(userId, EkycStatus.FAILED)).thenReturn(0L);

        EkycResult ekycResult = EkycResult.pass("MOCK", "REF1", 0.95, 0.99, 0.98);
        SellerRiskResult result = sellerRiskService.evaluateRisk(user, verification, ekycResult);

        assertEquals(RiskStatus.REVIEW, result.status());
        assertTrue(result.reasonCodes().contains(ReasonCode.DUPLICATE_IDENTITY));
    }

    @Test
    void evaluateRisk_WhenRepeatedFailuresExceedThreshold_ShouldReturnReview() {
        when(sellerVerificationRepository.findByDocumentNumberHash("hashed_1234")).thenReturn(List.of());
        when(sellerVerificationRepository.countByUserIdAndEkycStatus(userId, EkycStatus.FAILED)).thenReturn(3L);

        EkycResult ekycResult = EkycResult.pass("MOCK", "REF1", 0.95, 0.99, 0.98);
        SellerRiskResult result = sellerRiskService.evaluateRisk(user, verification, ekycResult);

        assertEquals(RiskStatus.REVIEW, result.status());
        assertTrue(result.reasonCodes().contains(ReasonCode.REPEATED_EKYC_FAILURES));
    }

    @Test
    void evaluateRisk_WhenBorderlineFaceMatchScore_ShouldReturnReview() {
        when(sellerVerificationRepository.findByDocumentNumberHash("hashed_1234")).thenReturn(List.of());
        when(sellerVerificationRepository.countByUserIdAndEkycStatus(userId, EkycStatus.FAILED)).thenReturn(0L);

        EkycResult ekycResult = EkycResult.pass("MOCK", "REF1", 0.80, 0.99, 0.98);
        SellerRiskResult result = sellerRiskService.evaluateRisk(user, verification, ekycResult);

        assertEquals(RiskStatus.REVIEW, result.status());
        assertTrue(result.reasonCodes().contains(ReasonCode.FACE_MATCH_BORDERLINE));
    }
}
