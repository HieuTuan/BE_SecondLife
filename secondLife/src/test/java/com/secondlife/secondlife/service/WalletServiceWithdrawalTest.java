package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.WalletMapper;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.repository.WalletWithdrawalRepository;
import com.secondlife.secondlife.service.impl.WalletServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceWithdrawalTest {

    @Mock
    private UserWalletRepository userWalletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WalletWithdrawalRepository walletWithdrawalRepository;

    private WalletServiceImpl walletService;

    private final UUID userId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private User user;
    private User admin;
    private UserWallet userWallet;

    @BeforeEach
    void setUp() {
        walletService = new WalletServiceImpl(
                userWalletRepository,
                walletTransactionRepository,
                userRepository,
                notificationService,
                walletWithdrawalRepository
        , MapperTestSupport.mapper(WalletMapper.class));

        user = new User();
        user.setId(userId);
        user.setEmail("user@example.com");

        admin = new User();
        admin.setId(adminId);
        admin.setEmail("admin@example.com");

        userWallet = new UserWallet();
        userWallet.setId(UUID.randomUUID());
        userWallet.setUser(user);
        userWallet.setBalance(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("requestWithdrawal - Thành công trừ tiền và tạo đơn rút tiền")
    void requestWithdrawal_WhenValid_ShouldDeductBalanceAndCreateWithdrawal() {
        WalletWithdrawalRequest request = new WalletWithdrawalRequest(
                new BigDecimal("200000"),
                "VCB",
                "1234567890",
                "NGUYEN VAN A"
        );

        when(userWalletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userWallet));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(invocation -> {
            WalletWithdrawal entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        WalletWithdrawalResponse response = walletService.requestWithdrawal(userId, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("200000"), response.amount());
        assertEquals("PENDING", response.status());
        assertEquals("VCB", response.bankCode());
        assertEquals("1234567890", response.accountNumber());
        assertEquals("NGUYEN VAN A", response.accountHolderName());

        // Wallet balance should be reduced from 1,000,000 to 800,000
        assertEquals(new BigDecimal("800000"), userWallet.getBalance());
        assertEquals(new BigDecimal("200000"), userWallet.getReservedBalance());
        verify(userWalletRepository).save(userWallet);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
        verify(notificationService).notifyWithEmail(eq(userId), anyString(), anyString(), any(NotificationType.class), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("requestWithdrawal - enforces 10,000 VND integer minimum")
    void requestWithdrawal_RejectsUnderMinimumAndFractionalVnd() {
        assertThrows(BadRequestException.class, () -> walletService.requestWithdrawal(userId,
                new WalletWithdrawalRequest(new BigDecimal("9999"), "VCB", "1234567890", "NGUYEN VAN A")));
        assertThrows(BadRequestException.class, () -> walletService.requestWithdrawal(userId,
                new WalletWithdrawalRequest(new BigDecimal("10000.5"), "VCB", "1234567890", "NGUYEN VAN A")));
        assertEquals(new BigDecimal("1000000"), userWallet.getBalance());
        verifyNoInteractions(userWalletRepository, walletWithdrawalRepository);
    }

    @Test
    @DisplayName("requestWithdrawal - Thất bại do số dư không đủ")
    void requestWithdrawal_WhenInsufficientBalance_ShouldThrowBadRequestException() {
        WalletWithdrawalRequest request = new WalletWithdrawalRequest(
                new BigDecimal("2000000"), // Exceeds 1,000,000
                "VCB",
                "1234567890",
                "NGUYEN VAN A"
        );

        when(userWalletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userWallet));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> walletService.requestWithdrawal(userId, request));

        assertTrue(ex.getMessage().contains("Số dư khả dụng không đủ"));
        assertEquals(new BigDecimal("1000000"), userWallet.getBalance());
        verify(walletWithdrawalRepository, never()).save(any());
    }

    @Test
    @DisplayName("reviewWithdrawal - Phê duyệt yêu cầu rút tiền thành công")
    void reviewWithdrawal_WhenApprove_ShouldSetStatusApprovedAndNotify() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A"
        );
        withdrawal.setId(withdrawalId);
        withdrawal.setStatus("PROCESSING");
        withdrawal.setEvidenceStorageId("report-evidence/test");
        withdrawal.setEvidenceUrl("/api/v1/wallets/withdrawals/evidence/" + withdrawalId);

        userWallet.setBalance(new BigDecimal("800000"));
        userWallet.setReservedBalance(new BigDecimal("200000"));

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userWalletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userWallet));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalReviewRequest reviewRequest = new WalletWithdrawalReviewRequest("APPROVE", "Đã chuyển tiền qua banking");

        WalletWithdrawalResponse response = walletService.reviewWithdrawal(adminId, withdrawalId, reviewRequest);

        assertNotNull(response);
        assertEquals("TRANSFERRED_AWAITING_REVIEW", response.status());
        assertEquals("Đã chuyển tiền qua banking", response.adminNote());
        assertEquals(adminId, response.reviewedBy());
        assertNotNull(response.reviewedAt());

        verify(walletWithdrawalRepository).save(withdrawal);
        verify(notificationService).notifyInApp(eq(userId), contains("đã được chuyển khoản"), anyString(), any(NotificationType.class), eq(withdrawalId), anyString(), anyString());
        assertEquals(BigDecimal.ZERO, userWallet.getReservedBalance());
        assertEquals(new BigDecimal("800000"), userWallet.getBalance());
        verify(userWalletRepository).findByUserIdWithLock(userId);
    }

    @Test
    @DisplayName("reviewWithdrawal - Cho phép tiếp nhận lại yêu cầu chuyển khoản thất bại")
    void reviewWithdrawal_WhenRetryFailedTransfer_ShouldReturnToProcessing() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A"
        );
        withdrawal.setId(withdrawalId);
        withdrawal.setStatus("TRANSFER_FAILED");
        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalResponse response = walletService.reviewWithdrawal(adminId, withdrawalId,
                new WalletWithdrawalReviewRequest("CLAIM", "Thử chuyển khoản lại"));

        assertEquals("PROCESSING", response.status());
        assertEquals(adminId, response.acceptedBy());
        verify(walletWithdrawalRepository).save(withdrawal);
    }

    @Test
    @DisplayName("reviewWithdrawal - Từ chối yêu cầu và hoàn trả tiền vào ví")
    void reviewWithdrawal_WhenReject_ShouldRefundAmountToWalletAndSetStatusRejected() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A"
        );
        withdrawal.setId(withdrawalId);

        // Current wallet balance is 800,000
        userWallet.setBalance(new BigDecimal("800000"));
        userWallet.setReservedBalance(new BigDecimal("200000"));

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userWalletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userWallet));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalReviewRequest reviewRequest = new WalletWithdrawalReviewRequest("REJECT", "Tài khoản ngân hàng sai thông tin");

        WalletWithdrawalResponse response = walletService.reviewWithdrawal(adminId, withdrawalId, reviewRequest);

        assertNotNull(response);
        assertEquals("REJECTED", response.status());
        assertEquals("Tài khoản ngân hàng sai thông tin", response.adminNote());

        // Refunded 200,000 back to wallet (800,000 + 200,000 = 1,000,000)
        assertEquals(new BigDecimal("1000000"), userWallet.getBalance());
        verify(userWalletRepository).save(userWallet);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
        verify(notificationService).notifyInApp(eq(userId), contains("đã bị từ chối"), anyString(), any(NotificationType.class), eq(withdrawalId), anyString(), anyString());
    }

    @Test
    @DisplayName("reviewWithdrawal - Báo lỗi khi đơn đã được xử lý trước đó")
    void reviewWithdrawal_WhenAlreadyProcessed_ShouldThrowBadRequestException() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A"
        );
        withdrawal.setStatus("COMPLETED");

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

        WalletWithdrawalReviewRequest reviewRequest = new WalletWithdrawalReviewRequest("REJECT", "Không duyệt lại");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> walletService.reviewWithdrawal(adminId, withdrawalId, reviewRequest));

        assertTrue(ex.getMessage().contains("chỉ có thể bị từ chối"));
    }

    @Test
    @DisplayName("getMyWithdrawals - Trả về phân trang lịch sử rút tiền của người dùng")
    void getMyWithdrawals_ShouldReturnPagedResponse() {
        Pageable pageable = PageRequest.of(0, 10);
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("100000"), "MB", "999999999", "NGUYEN VAN A"
        );
        withdrawal.setId(UUID.randomUUID());

        Page<WalletWithdrawal> page = new PageImpl<>(List.of(withdrawal), pageable, 1);
        when(walletWithdrawalRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)).thenReturn(page);

        Page<WalletWithdrawalResponse> result = walletService.getMyWithdrawals(userId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("MB", result.getContent().get(0).bankCode());
    }

    @Test
    @DisplayName("getAllWithdrawals - Lọc theo trạng thái cho Admin")
    void getAllWithdrawals_WithStatus_ShouldFilterByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("500000"), "TCB", "888888888", "TRAN VAN B"
        );
        withdrawal.setId(UUID.randomUUID());

        Page<WalletWithdrawal> page = new PageImpl<>(List.of(withdrawal), pageable, 1);
        when(walletWithdrawalRepository.findByStatusOrderByCreatedAtDesc("PENDING", pageable)).thenReturn(page);

        Page<WalletWithdrawalResponse> result = walletService.getAllWithdrawals("PENDING", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(walletWithdrawalRepository).findByStatusOrderByCreatedAtDesc("PENDING", pageable);
    }

    @Test
    @DisplayName("requestWithdrawal - Từ chối khi ví bị đóng băng hoặc đạt giới hạn 3 lệnh PENDING")
    void requestWithdrawal_RejectsFrozenWalletAndMaxPendingLimit() {
        WalletWithdrawalRequest request = new WalletWithdrawalRequest(
                new BigDecimal("100000"), "VCB", "1234567890", "NGUYEN VAN A");

        userWallet.setStatus("FROZEN");
        when(userWalletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userWallet));
        assertThrows(BadRequestException.class, () -> walletService.requestWithdrawal(userId, request));

        userWallet.setStatus("ACTIVE");
        when(walletWithdrawalRepository.countByUserIdAndStatus(userId, "PENDING")).thenReturn(3L);
        assertThrows(BadRequestException.class, () -> walletService.requestWithdrawal(userId, request));

        when(walletWithdrawalRepository.countByUserIdAndStatus(userId, "PENDING")).thenReturn(0L);
        user.setAccountStatus(AccountStatus.LOCKED);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        assertThrows(BadRequestException.class, () -> walletService.requestWithdrawal(userId, request));
    }

    @Test
    @DisplayName("reviewWithdrawal - Chặn nhân viên khác thao tác lệnh đã nhận và cho phép Admin thu hồi UNCLAIM")
    void reviewWithdrawal_EnforcesAssignedStaffLockAndAllowsAdminUnclaim() {
        UUID withdrawalId = UUID.randomUUID();
        UUID staffAId = UUID.randomUUID();
        UUID staffBId = UUID.randomUUID();
        User staffA = new User();
        staffA.setId(staffAId);
        User staffB = new User();
        staffB.setId(staffBId);

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        admin.addRole(adminRole);

        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A");
        withdrawal.setId(withdrawalId);
        withdrawal.setStatus("PROCESSING");
        withdrawal.setAcceptedBy(staffA);
        withdrawal.setEvidenceStorageId("evidence/old");
        withdrawal.setEvidenceUrl("/api/v1/wallets/withdrawals/evidence/" + withdrawalId);

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(staffBId)).thenReturn(Optional.of(staffB));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(ConflictException.class, () -> walletService.reviewWithdrawal(
                staffBId, withdrawalId, new WalletWithdrawalReviewRequest("CONFIRM_TRANSFER", "Try override")));
        assertThrows(ConflictException.class, () -> walletService.reviewWithdrawal(
                staffBId, withdrawalId, new WalletWithdrawalReviewRequest("TRANSFER_FAILED", "Try override")));

        WalletWithdrawalResponse unclaimed = walletService.reviewWithdrawal(
                adminId, withdrawalId, new WalletWithdrawalReviewRequest("UNCLAIM", "Thu hồi xử lý"));
        assertEquals("PENDING", unclaimed.status());
        assertNull(unclaimed.acceptedBy());
    }

    @Test
    @DisplayName("reviewWithdrawal - TRANSFER_FAILED xóa ảnh minh chứng cũ để bắt buộc tải ảnh mới")
    void reviewWithdrawal_TransferFailedClearsStaleEvidence() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A");
        withdrawal.setId(withdrawalId);
        withdrawal.setStatus("PROCESSING");
        withdrawal.setAcceptedBy(admin);
        withdrawal.setEvidenceStorageId("evidence/stale");
        withdrawal.setEvidenceUrl("/api/v1/wallets/withdrawals/evidence/" + withdrawalId);

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalResponse res = walletService.reviewWithdrawal(
                adminId, withdrawalId, new WalletWithdrawalReviewRequest("TRANSFER_FAILED", "Ngân hàng bảo trì"));
        assertEquals("TRANSFER_FAILED", res.status());
        assertNull(withdrawal.getEvidenceUrl());
        assertNull(withdrawal.getEvidenceStorageId());
        verify(notificationService).notifyInApp(eq(userId), contains("chưa hoàn tất"), anyString(), any(NotificationType.class), eq(withdrawalId), anyString(), anyString());
    }

    @Test
    @DisplayName("Dispute lifecycle - Lưu ảnh bằng chứng user, báo Staff, DISPUTE_PROCESSING và hoàn tiền khi RESOLVED_REFUND_TO_WALLET")
    void disputeLifecycle_SupportsEvidenceStartReviewAndRefundOutcome() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A");
        withdrawal.setId(withdrawalId);
        withdrawal.setStatus("TRANSFERRED_AWAITING_REVIEW");
        withdrawal.setAcceptedBy(admin);
        withdrawal.setDisputeDeadline(java.time.Instant.now().plusSeconds(3600));
        userWallet.setBalance(new BigDecimal("800000"));
        userWallet.setReservedBalance(BigDecimal.ZERO);

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userWalletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userWallet));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalResponse disputed = walletService.reportWithdrawalIssue(
                userId, withdrawalId, "NOT_RECEIVED", "Chưa thấy tiền vào tài khoản",
                List.of("https://cdn.example.com/dispute-1.png"));
        assertEquals("DISPUTED", disputed.status());
        assertEquals(List.of("https://cdn.example.com/dispute-1.png"), disputed.disputeEvidenceUrls());
        verify(notificationService).notifyInApp(eq(adminId), contains("báo cáo vấn đề"), anyString(), any(NotificationType.class), eq(withdrawalId), anyString(), anyString());

        WalletWithdrawalResponse inReview = walletService.reviewWithdrawal(
                adminId, withdrawalId, new WalletWithdrawalReviewRequest("START_DISPUTE_REVIEW", "Đang kiểm tra sao kê"));
        assertEquals("DISPUTE_PROCESSING", inReview.status());

        WalletWithdrawalResponse resolved = walletService.reviewWithdrawal(
                adminId, withdrawalId, new WalletWithdrawalReviewRequest(
                        "RESOLVE_DISPUTE", "Giao dịch bị ngân hàng hoàn trả, đã hoàn vào ví",
                        "https://cdn.example.com/bank-reversal.png", null, List.of(),
                        "RESOLVED_REFUND_TO_WALLET", null));
        assertEquals("DISPUTE_RESOLVED", resolved.status());
        assertEquals("RESOLVED_REFUND_TO_WALLET", resolved.disputeResolutionOutcome());
        assertEquals("https://cdn.example.com/bank-reversal.png", resolved.disputeResolutionEvidenceUrl());
        assertEquals(new BigDecimal("1000000"), userWallet.getBalance());
    }

    @Test
    @DisplayName("requestWithdrawalException - Ghi nhận ngoại lệ sau 48h không tự động hoàn tiền và giữ nguyên COMPLETED")
    void requestWithdrawalException_RecordsPost48hExceptionWithoutAutoRefund() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A");
        withdrawal.setId(withdrawalId);
        withdrawal.setStatus("COMPLETED");
        withdrawal.setAcceptedBy(admin);
        userWallet.setBalance(new BigDecimal("800000"));

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalResponse res = walletService.requestWithdrawalException(
                userId, withdrawalId, "Sao kê ngân hàng tháng chưa có khoản chuyển này",
                List.of("https://cdn.example.com/statement.png"));
        assertEquals("COMPLETED", res.status());
        assertNotNull(res.exceptionRequestedAt());
        assertEquals("Sao kê ngân hàng tháng chưa có khoản chuyển này", res.exceptionReason());
        assertEquals(List.of("https://cdn.example.com/statement.png"), res.exceptionEvidenceUrls());
        assertEquals(new BigDecimal("800000"), userWallet.getBalance());
        assertThrows(ConflictException.class, () -> walletService.requestWithdrawalException(
                userId, withdrawalId, "Gửi lại lần 2", List.of()));
    }

    @Test
    @DisplayName("confirmReceipt - Cho phép người dùng đóng báo cáo đang mở và xác nhận đã nhận đủ tiền")
    void confirmReceipt_ClosesDisputeAndCompletesWithdrawal() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "TPB", "14200414210", "NGUYEN VAN A");
        withdrawal.setId(withdrawalId);
        withdrawal.setRequestCode("SLWD-CLOSE01");
        withdrawal.setStatus("DISPUTED");
        withdrawal.setDisputeType("NOT_RECEIVED");
        withdrawal.setDisputeDescription("Chưa nhận được tiền");
        withdrawal.setAcceptedBy(admin);

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalResponse completed = walletService.confirmReceipt(userId, withdrawalId);
        assertEquals("COMPLETED", completed.status());
        assertEquals("RESOLVED_VALID_TRANSFER", completed.disputeResolutionOutcome());
        assertNotNull(completed.completedAt());
    }
}
