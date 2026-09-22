package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationResubmitRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.dto.risk.SellerRiskResult;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.SellerVerificationMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.SellerVerificationEventRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.SellerVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SellerVerificationServiceTest {

    @Mock
    private SellerVerificationRepository sellerVerificationRepository;

    @Mock
    private SellerVerificationEventRepository sellerVerificationEventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EkycService ekycService;

    @Mock
    private SellerRiskService sellerRiskService;

    @Mock
    private SellerVerificationMapper sellerVerificationMapper;

    @Mock
    private NotificationService notificationService;

    private SellerVerificationServiceImpl service;

    private User buyer;
    private User admin;
    private Role sellerRole;
    private UUID buyerId;
    private UUID adminId;
    private UUID verificationId;

    @BeforeEach
    void setUp() throws Exception {
        service = new SellerVerificationServiceImpl(
                sellerVerificationRepository,
                sellerVerificationEventRepository,
                userRepository,
                roleRepository,
                ekycService,
                sellerRiskService,
                sellerVerificationMapper,
                notificationService,
                3 // maxResubmissions
        );

        buyerId = UUID.randomUUID();
        buyer = new User("buyer@example.com", "pass", AccountStatus.ACTIVE);
        Field idField1 = User.class.getDeclaredField("id");
        idField1.setAccessible(true);
        idField1.set(buyer, buyerId);

        adminId = UUID.randomUUID();
        admin = new User("admin@example.com", "pass", AccountStatus.ACTIVE);
        Field idField2 = User.class.getDeclaredField("id");
        idField2.setAccessible(true);
        idField2.set(admin, adminId);

        sellerRole = new Role(RoleCode.SELLER.name(), "Seller", "Seller role");
        verificationId = UUID.randomUUID();

        lenient().when(sellerVerificationMapper.toResponse(any())).thenAnswer(inv -> {
            SellerVerification sv = inv.getArgument(0);
            return SellerVerificationResponse.from(sv);
        });
    }

    @Test
    @DisplayName("CASE A: eKYC PASS + Risk CLEAR -> Auto APPROVE, SELLER assigned, ReviewSource.SYSTEM")
    void testCaseA_EkycPass_RiskClear_ShouldAutoApproveAndGrantSeller() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.findByCodeWithPermissions(RoleCode.SELLER.name())).thenReturn(Optional.of(sellerRole));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.pass("MOCK", "REF1", 0.98, 0.99, 0.95)
        );
        when(sellerRiskService.evaluateRisk(eq(buyer), any(), any())).thenReturn(
                SellerRiskResult.clear()
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertNotNull(response);
        assertEquals(SellerVerificationStatus.APPROVED, response.status());
        assertEquals(EkycStatus.PASSED, response.ekycStatus());
        assertEquals(RiskStatus.CLEAR, response.riskStatus());
        assertEquals(ReviewSource.SYSTEM, response.reviewSource());
        assertTrue(buyer.hasRole(RoleCode.SELLER.name()));

        verify(notificationService).sendSellerVerificationApproved(buyer);
    }

    @Test
    @DisplayName("CASE B: eKYC PASS + Risk REVIEW -> NEEDS_REVIEW, SELLER not assigned")
    void testCaseB_EkycPass_RiskReview_ShouldSetNeedsReviewAndNotGrantSeller() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.pass("MOCK", "REF1", 0.82, 0.99, 0.95)
        );
        when(sellerRiskService.evaluateRisk(eq(buyer), any(), any())).thenReturn(
                SellerRiskResult.review(List.of(ReasonCode.FACE_MATCH_BORDERLINE), 60.0, "Borderline face match")
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertNotNull(response);
        assertEquals(SellerVerificationStatus.NEEDS_REVIEW, response.status());
        assertEquals(EkycStatus.PASSED, response.ekycStatus());
        assertEquals(RiskStatus.REVIEW, response.riskStatus());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
        verify(roleRepository, never()).findByCodeWithPermissions(any());
        verify(notificationService, never()).sendSellerVerificationApproved(any());
    }

    @Test
    @DisplayName("CASE C: eKYC PASS + Risk BLOCK -> REJECTED, SELLER not assigned")
    void testCaseC_EkycPass_RiskBlock_ShouldAutoRejectAndNotGrantSeller() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.pass("MOCK", "REF1", 0.98, 0.99, 0.95)
        );
        when(sellerRiskService.evaluateRisk(eq(buyer), any(), any())).thenReturn(
                SellerRiskResult.block(List.of(ReasonCode.ACCOUNT_LOCKED), "Account locked")
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertNotNull(response);
        assertEquals(SellerVerificationStatus.REJECTED, response.status());
        assertEquals(ReviewSource.SYSTEM, response.reviewSource());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
        verify(notificationService).sendSellerVerificationRejected(eq(buyer), anyString());
    }

    @Test
    @DisplayName("CASE D: Document Expired -> FAILED, REJECTED")
    void testCaseD_DocumentExpired_ShouldFailAndAutoReject() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901_FAIL_EXPIRED", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.fail(ReasonCode.DOCUMENT_EXPIRED, "MOCK", "REF1")
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertEquals(SellerVerificationStatus.REJECTED, response.status());
        assertEquals(EkycStatus.FAILED, response.ekycStatus());
        assertEquals(ReasonCode.DOCUMENT_EXPIRED, response.reasonCode());
        assertEquals(ReviewSource.SYSTEM, response.reviewSource());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
        verify(notificationService).sendSellerVerificationRejected(eq(buyer), anyString());
    }

    @Test
    @DisplayName("CASE E: Liveness Failure -> FAILED, REJECTED")
    void testCaseE_LivenessFailed_ShouldFailAndAutoReject() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901_FAIL_LIVENESS", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.fail(ReasonCode.LIVENESS_FAILED, "MOCK", "REF1")
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertEquals(SellerVerificationStatus.REJECTED, response.status());
        assertEquals(EkycStatus.FAILED, response.ekycStatus());
        assertEquals(ReasonCode.LIVENESS_FAILED, response.reasonCode());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
    }

    @Test
    @DisplayName("CASE F: Blurry Document (User-fixable) -> UNCERTAIN, RESUBMIT_REQUIRED")
    void testCaseF_BlurryDocument_ShouldSetResubmitRequired() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901_UNCERTAIN_BLURRY", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.uncertain(ReasonCode.IMAGE_TOO_BLURRY, "MOCK", "REF1", 0.4, 0.5, 0.3)
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertEquals(SellerVerificationStatus.RESUBMIT_REQUIRED, response.status());
        assertEquals(EkycStatus.UNCERTAIN, response.ekycStatus());
        assertEquals(ReasonCode.IMAGE_TOO_BLURRY, response.reasonCode());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
    }

    @Test
    @DisplayName("CASE G: Borderline Face Match (Non-fixable) -> UNCERTAIN, NEEDS_REVIEW")
    void testCaseG_BorderlineFaceMatch_ShouldSetNeedsReview() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901_UNCERTAIN_BORDERLINE", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.uncertain(ReasonCode.FACE_MATCH_BORDERLINE, "MOCK", "REF1", 0.72, 0.85, 0.88)
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertEquals(SellerVerificationStatus.NEEDS_REVIEW, response.status());
        assertEquals(EkycStatus.UNCERTAIN, response.ekycStatus());
        assertEquals(ReasonCode.FACE_MATCH_BORDERLINE, response.reasonCode());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
    }

    @Test
    @DisplayName("CASE H & I: Provider Timeout / Unavailable -> EKYC_PENDING (NOT rejected)")
    void testCaseH_ProviderTimeout_ShouldKeepEkycPending() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901_ERR_TIMEOUT", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.providerError(ReasonCode.PROVIDER_TIMEOUT, "MOCK", "REF1", "timeout")
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertEquals(SellerVerificationStatus.EKYC_PENDING, response.status());
        assertEquals(EkycStatus.PROVIDER_ERROR, response.ekycStatus());
        assertNotEquals(SellerVerificationStatus.REJECTED, response.status());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
    }

    @Test
    @DisplayName("CASE J: Admin approves NEEDS_REVIEW -> APPROVED, ReviewSource.ADMIN, SELLER assigned")
    void testCaseJ_AdminApprovesNeedsReview_ShouldApproveAndAssignSeller() {
        SellerVerification sv = new SellerVerification(buyer, VerificationType.CITIZEN_ID, "123", "f", "b");
        sv.setStatus(SellerVerificationStatus.NEEDS_REVIEW);

        when(sellerVerificationRepository.findByIdWithDetails(verificationId)).thenReturn(Optional.of(sv));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCodeWithPermissions(RoleCode.SELLER.name())).thenReturn(Optional.of(sellerRole));
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenReturn(sv);

        SellerVerificationResponse response = service.approveVerification(adminId, verificationId);

        assertEquals(SellerVerificationStatus.APPROVED, sv.getStatus());
        assertEquals(ReviewSource.ADMIN, sv.getReviewSource());
        assertEquals(admin, sv.getReviewedBy());
        assertTrue(buyer.hasRole(RoleCode.SELLER.name()));
        verify(notificationService).sendSellerVerificationApproved(buyer);
    }

    @Test
    @DisplayName("CASE K: Admin rejects NEEDS_REVIEW -> REJECTED, reason saved, SELLER not assigned")
    void testCaseK_AdminRejectsNeedsReview_ShouldRejectAndNotAssignSeller() {
        SellerVerification sv = new SellerVerification(buyer, VerificationType.CITIZEN_ID, "123", "f", "b");
        sv.setStatus(SellerVerificationStatus.NEEDS_REVIEW);
        SellerVerificationReviewRequest reviewReq = new SellerVerificationReviewRequest("Documents are unreadable");

        when(sellerVerificationRepository.findByIdWithDetails(verificationId)).thenReturn(Optional.of(sv));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenReturn(sv);

        SellerVerificationResponse response = service.rejectVerification(adminId, verificationId, reviewReq);

        assertEquals(SellerVerificationStatus.REJECTED, sv.getStatus());
        assertEquals(ReviewSource.ADMIN, sv.getReviewSource());
        assertEquals("Documents are unreadable", sv.getRejectionReason());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
        verify(notificationService).sendSellerVerificationRejected(buyer, "Documents are unreadable");
    }

    @Test
    @DisplayName("CASE L: Resubmission flow and rejection of resubmitting APPROVED status")
    void testCaseL_ResubmitValidation() {
        SellerVerification sv = new SellerVerification(buyer, VerificationType.CITIZEN_ID, "123", "f", "b");
        sv.setStatus(SellerVerificationStatus.APPROVED);

        when(sellerVerificationRepository.findByIdWithDetails(verificationId)).thenReturn(Optional.of(sv));

        SellerVerificationResubmitRequest resubmitReq = new SellerVerificationResubmitRequest("http://f2", "http://b2");

        assertThrows(ConflictException.class, () -> service.resubmitVerification(buyerId, verificationId, resubmitReq));
    }

    @Test
    @DisplayName("CASE M: User tries to submit when already a SELLER -> 409 Conflict")
    void testCaseM_UserAlreadySeller_ShouldThrowConflict() {
        buyer.addRole(sellerRole);
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));

        assertThrows(ConflictException.class, () -> service.submitVerification(buyerId, request));
        verify(sellerVerificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("CASE N: Duplicate Identity detection -> REVIEW")
    void testCaseN_DuplicateIdentity_ShouldTriggerReview() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "012345678901", "http://f", "http://b"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatusIn(eq(buyerId), anyCollection())).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        when(ekycService.verifyIdentity(any())).thenReturn(
                EkycResult.pass("MOCK", "REF1", 0.98, 0.99, 0.95)
        );
        when(sellerRiskService.evaluateRisk(eq(buyer), any(), any())).thenReturn(
                SellerRiskResult.review(List.of(ReasonCode.DUPLICATE_IDENTITY), 85.0, "Duplicate document number")
        );

        SellerVerificationResponse response = service.submitVerification(buyerId, request);

        assertEquals(SellerVerificationStatus.NEEDS_REVIEW, response.status());
        assertEquals(ReasonCode.DUPLICATE_IDENTITY, response.reasonCode());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
    }
}
