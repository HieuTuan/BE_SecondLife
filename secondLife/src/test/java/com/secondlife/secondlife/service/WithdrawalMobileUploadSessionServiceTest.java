package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.MediaUploadResponse;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.dto.response.WithdrawalMobileUploadSessionResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.WalletWithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithdrawalMobileUploadSessionServiceTest {

    @Mock
    private WalletWithdrawalRepository walletWithdrawalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private MediaService mediaService;

    private WithdrawalMobileUploadSessionService service;

    @BeforeEach
    void setUp() {
        service = new WithdrawalMobileUploadSessionService(
                walletWithdrawalRepository,
                userRepository,
                walletService,
                mediaService
        );
    }

    @Test
    void createAndUploadWithSingleUseTokenUpdatesStatusAndRejectsReplay() {
        UUID staffId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();

        User staff = new User("staff@secondlife.vn", "hash", AccountStatus.ACTIVE);
        staff.setId(staffId);
        Role staffRole = new Role();
        staffRole.setCode("STAFF");
        staff.addRole(staffRole);

        WalletWithdrawal withdrawal = new WalletWithdrawal();
        withdrawal.setId(withdrawalId);
        withdrawal.setRequestCode("SLWD-TEST0001");
        withdrawal.setStatus("PROCESSING");
        withdrawal.setAcceptedBy(staff);
        withdrawal.setAmount(new BigDecimal("250000"));
        withdrawal.setBankCode("TPB");
        withdrawal.setBankName("TPBank");
        withdrawal.setAccountNumber("14200414210");
        withdrawal.setAccountHolderName("NGUYEN VAN A");

        when(walletWithdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        WithdrawalMobileUploadSessionResponse created = service.createSession(staffId, withdrawalId);
        assertNotNull(created.sessionId());
        assertNotNull(created.token());
        assertEquals("PENDING", created.status());
        assertEquals("STAFF_RECEIPT", created.purpose());
        assertEquals(1, created.maxFiles());
        assertEquals("••••4210", created.maskedAccountNumber());

        WithdrawalMobileUploadSessionResponse publicInfo = service.getPublicSessionInfo(created.token());
        assertNull(publicInfo.token());
        assertEquals("SLWD-TEST0001", publicInfo.requestCode());
        assertEquals("PENDING", publicInfo.status());

        MockMultipartFile file = new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});
        String evidenceUrl = "/api/v1/wallets/withdrawals/evidence/" + withdrawalId;
        when(walletService.uploadWithdrawalEvidence(eq(staffId), eq(withdrawalId), any()))
                .thenReturn(WalletWithdrawalResponse.builder()
                        .id(withdrawalId)
                        .status("PROCESSING")
                        .evidenceUrl(evidenceUrl)
                        .build());

        WithdrawalMobileUploadSessionResponse uploaded = service.uploadEvidenceWithToken(created.token(), file);
        assertEquals("UPLOADED", uploaded.status());
        assertEquals(evidenceUrl, uploaded.evidenceUrl());
        assertEquals(List.of(evidenceUrl), uploaded.evidenceUrls());

        WithdrawalMobileUploadSessionResponse polled = service.getSessionStatusForStaff(staffId, withdrawalId, created.sessionId());
        assertEquals("UPLOADED", polled.status());
        assertEquals(evidenceUrl, polled.evidenceUrl());

        assertThrows(ConflictException.class, () -> service.uploadEvidenceWithToken(created.token(), file));
    }

    @Test
    void createSessionRejectsNonProcessingOrUnassignedStaff() {
        UUID staffId = UUID.randomUUID();
        UUID otherStaffId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();

        User assignedStaff = new User("staff1@secondlife.vn", "hash", AccountStatus.ACTIVE);
        assignedStaff.setId(staffId);
        User otherStaff = new User("staff2@secondlife.vn", "hash", AccountStatus.ACTIVE);
        otherStaff.setId(otherStaffId);

        WalletWithdrawal withdrawal = new WalletWithdrawal();
        withdrawal.setId(withdrawalId);
        withdrawal.setStatus("PENDING");
        withdrawal.setAcceptedBy(assignedStaff);

        when(walletWithdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));
        assertThrows(ConflictException.class, () -> service.createSession(staffId, withdrawalId));

        withdrawal.setStatus("PROCESSING");
        when(userRepository.findById(otherStaffId)).thenReturn(Optional.of(otherStaff));
        assertThrows(ConflictException.class, () -> service.createSession(otherStaffId, withdrawalId));

        assertThrows(NotFoundException.class, () -> service.getPublicSessionInfo("invalid-token"));
    }

    @Test
    void userDisputeMobileUploadSupportsMultipleEvidenceImagesUpToMaxFiles() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();

        User owner = new User("buyer@secondlife.vn", "hash", AccountStatus.ACTIVE);
        owner.setId(userId);

        WalletWithdrawal withdrawal = new WalletWithdrawal();
        withdrawal.setId(withdrawalId);
        withdrawal.setUser(owner);
        withdrawal.setRequestCode("SLWD-USER0001");
        withdrawal.setStatus("TRANSFERRED_AWAITING_REVIEW");
        withdrawal.setDisputeDeadline(Instant.now().plusSeconds(3600));
        withdrawal.setAmount(new BigDecimal("100000"));
        withdrawal.setBankCode("TPB");
        withdrawal.setBankName("TPBank");
        withdrawal.setAccountNumber("14200414210");
        withdrawal.setAccountHolderName("PHAM DO DINH PHAT");

        when(walletWithdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));

        assertThrows(ConflictException.class, () -> service.createUserSession(otherUserId, withdrawalId, "USER_DISPUTE", 2));

        WithdrawalMobileUploadSessionResponse created = service.createUserSession(userId, withdrawalId, "USER_DISPUTE", 2);
        assertEquals("USER_DISPUTE", created.purpose());
        assertEquals(2, created.maxFiles());
        assertEquals("PENDING", created.status());

        MockMultipartFile file1 = new MockMultipartFile("file", "stmt1.png", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile file2 = new MockMultipartFile("file", "stmt2.png", "image/png", new byte[]{4, 5, 6});

        when(mediaService.uploadImage(any(), eq("secondlife/withdrawals/disputes")))
                .thenReturn(new MediaUploadResponse("https://cdn.example/stmt1.png", "p1", "png", 3L, "stmt1.png"))
                .thenReturn(new MediaUploadResponse("https://cdn.example/stmt2.png", "p2", "png", 3L, "stmt2.png"));

        WithdrawalMobileUploadSessionResponse firstUpload = service.uploadEvidenceWithToken(created.token(), file1);
        assertEquals("PENDING", firstUpload.status());
        assertEquals(List.of("https://cdn.example/stmt1.png"), firstUpload.evidenceUrls());

        WithdrawalMobileUploadSessionResponse secondUpload = service.uploadEvidenceWithToken(created.token(), file2);
        assertEquals("UPLOADED", secondUpload.status());
        assertEquals(List.of("https://cdn.example/stmt1.png", "https://cdn.example/stmt2.png"), secondUpload.evidenceUrls());

        WithdrawalMobileUploadSessionResponse polled = service.getSessionStatusForUser(userId, withdrawalId, created.sessionId());
        assertEquals("UPLOADED", polled.status());
        assertEquals(2, polled.evidenceUrls().size());

        assertThrows(ConflictException.class, () -> service.uploadEvidenceWithToken(created.token(), file1));
    }

    @Test
    void enforcesConfiguredMaxEvidenceFileBytesFromEnvironment() {
        service.setMaxEvidenceFileBytes(2L * 1024 * 1024);

        UUID userId = UUID.randomUUID();
        UUID withdrawalId = UUID.randomUUID();

        User owner = new User("buyer@secondlife.vn", "hash", AccountStatus.ACTIVE);
        owner.setId(userId);

        WalletWithdrawal withdrawal = new WalletWithdrawal();
        withdrawal.setId(withdrawalId);
        withdrawal.setUser(owner);
        withdrawal.setRequestCode("SLWD-LIMIT01");
        withdrawal.setStatus("TRANSFERRED_AWAITING_REVIEW");
        withdrawal.setDisputeDeadline(Instant.now().plusSeconds(3600));
        withdrawal.setAmount(new BigDecimal("100000"));
        withdrawal.setBankCode("TPB");
        withdrawal.setBankName("TPBank");
        withdrawal.setAccountNumber("14200414210");
        withdrawal.setAccountHolderName("PHAM DO DINH PHAT");

        when(walletWithdrawalRepository.findById(withdrawalId)).thenReturn(Optional.of(withdrawal));

        WithdrawalMobileUploadSessionResponse created = service.createUserSession(userId, withdrawalId, "USER_DISPUTE", 1);
        assertEquals(2L * 1024 * 1024, created.maxFileSizeBytes());

        byte[] oversized = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile tooBig = new MockMultipartFile("file", "big.jpg", "image/jpeg", oversized);
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> service.uploadEvidenceWithToken(created.token(), tooBig)
        );
        assertTrue(ex.getMessage().contains("2 MB"));
    }
}
