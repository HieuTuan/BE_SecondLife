package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.enums.VerificationStatus;
import com.secondlife.secondlife.enums.VerificationType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.SellerVerificationMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.SellerVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
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
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SellerVerificationMapper sellerVerificationMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private SellerVerificationServiceImpl service;

    private User buyer;
    private User admin;
    private Role sellerRole;
    private UUID buyerId;
    private UUID adminId;
    private UUID verificationId;

    @BeforeEach
    void setUp() throws Exception {
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
    }

    @Test
    void submitVerification_WhenValid_ShouldSavePendingVerification() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "123456789", "http://front.url", "http://back.url"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatus(buyerId, VerificationStatus.PENDING)).thenReturn(false);
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        SellerVerificationResponse response = new SellerVerificationResponse(
                verificationId, buyerId, buyer.getEmail(), null, VerificationType.CITIZEN_ID,
                "123456789", "http://front.url", "http://back.url",
                VerificationStatus.PENDING, null, null, null, null
        );
        when(sellerVerificationMapper.toResponse(any())).thenReturn(response);

        SellerVerificationResponse result = service.submitVerification(buyerId, request);

        assertNotNull(result);
        assertEquals(VerificationStatus.PENDING, result.status());
        verify(sellerVerificationRepository).save(any(SellerVerification.class));
    }

    @Test
    void submitVerification_WhenUserAlreadySeller_ShouldThrowConflict() {
        buyer.addRole(sellerRole);
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "123456789", "http://front.url", "http://back.url"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));

        assertThrows(ConflictException.class, () -> service.submitVerification(buyerId, request));
        verify(sellerVerificationRepository, never()).save(any());
    }

    @Test
    void submitVerification_WhenPendingAlreadyExists_ShouldThrowConflict() {
        SellerVerificationRequest request = new SellerVerificationRequest(
                VerificationType.CITIZEN_ID, "123456789", "http://front.url", "http://back.url"
        );

        when(userRepository.findByIdWithAuthorities(buyerId)).thenReturn(Optional.of(buyer));
        when(sellerVerificationRepository.existsByUserIdAndStatus(buyerId, VerificationStatus.PENDING)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.submitVerification(buyerId, request));
        verify(sellerVerificationRepository, never()).save(any());
    }

    @Test
    void approveVerification_WhenPending_ShouldApproveAndAssignSellerRole() {
        SellerVerification sv = new SellerVerification(buyer, VerificationType.CITIZEN_ID, "123", "f", "b");

        when(sellerVerificationRepository.findByIdWithDetails(verificationId)).thenReturn(Optional.of(sv));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(roleRepository.findByCodeWithPermissions(RoleCode.SELLER.name())).thenReturn(Optional.of(sellerRole));
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenReturn(sv);

        service.approveVerification(adminId, verificationId);

        assertEquals(VerificationStatus.APPROVED, sv.getStatus());
        assertEquals(admin, sv.getReviewedBy());
        assertNotNull(sv.getReviewedAt());
        assertTrue(buyer.hasRole(RoleCode.SELLER.name()));
        verify(userRepository).save(buyer);
        verify(sellerVerificationRepository).save(sv);
        verify(notificationService).sendSellerVerificationApproved(buyer);
    }

    @Test
    void approveVerification_WhenNotPending_ShouldThrowBadRequest() {
        SellerVerification sv = new SellerVerification(buyer, VerificationType.CITIZEN_ID, "123", "f", "b");
        sv.setStatus(VerificationStatus.APPROVED);

        when(sellerVerificationRepository.findByIdWithDetails(verificationId)).thenReturn(Optional.of(sv));

        assertThrows(BadRequestException.class, () -> service.approveVerification(adminId, verificationId));
    }

    @Test
    void rejectVerification_WhenPending_ShouldSetRejectedAndNotAssignSellerRole() {
        SellerVerification sv = new SellerVerification(buyer, VerificationType.CITIZEN_ID, "123", "f", "b");
        SellerVerificationReviewRequest request = new SellerVerificationReviewRequest("Document is blurry");

        when(sellerVerificationRepository.findByIdWithDetails(verificationId)).thenReturn(Optional.of(sv));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(sellerVerificationRepository.save(any(SellerVerification.class))).thenReturn(sv);

        service.rejectVerification(adminId, verificationId, request);

        assertEquals(VerificationStatus.REJECTED, sv.getStatus());
        assertEquals("Document is blurry", sv.getRejectionReason());
        assertEquals(admin, sv.getReviewedBy());
        assertFalse(buyer.hasRole(RoleCode.SELLER.name()));
        verify(userRepository, never()).save(buyer);
        verify(sellerVerificationRepository).save(sv);
        verify(notificationService).sendSellerVerificationRejected(buyer, "Document is blurry");
    }
}
