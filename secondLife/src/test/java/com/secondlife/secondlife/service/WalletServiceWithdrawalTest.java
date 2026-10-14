package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.entity.WalletWithdrawal;
import com.secondlife.secondlife.enums.WalletTransactionType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.repository.WalletWithdrawalRepository;
import com.secondlife.secondlife.service.impl.WalletServiceImpl;
import com.secondlife.secondlife.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        );

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
        assertEquals(new BigDecimal("200000"), response.getAmount());
        assertEquals("PENDING_REVIEW", response.getStatus());
        assertEquals("VCB", response.getBankCode());
        assertEquals("1234567890", response.getAccountNumber());
        assertEquals("NGUYEN VAN A", response.getAccountHolderName());

        // Wallet balance should be reduced from 1,000,000 to 800,000
        assertEquals(new BigDecimal("800000"), userWallet.getBalance());
        verify(userWalletRepository).save(userWallet);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
        verify(notificationService).notifyWithEmail(eq(userId), anyString(), anyString(), any(NotificationType.class), any(), anyString(), anyString());
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

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalReviewRequest reviewRequest = new WalletWithdrawalReviewRequest("APPROVE", "Đã chuyển tiền qua banking");

        WalletWithdrawalResponse response = walletService.reviewWithdrawal(adminId, withdrawalId, reviewRequest);

        assertNotNull(response);
        assertEquals("APPROVED", response.getStatus());
        assertEquals("Đã chuyển tiền qua banking", response.getAdminNote());
        assertEquals(adminId, response.getReviewedBy());
        assertNotNull(response.getReviewedAt());

        verify(walletWithdrawalRepository).save(withdrawal);
        verify(notificationService).notifyWithEmail(eq(userId), contains("phê duyệt"), anyString(), any(NotificationType.class), eq(withdrawalId), anyString(), anyString());
        // No refund to wallet on approval
        verify(userWalletRepository, never()).findByUserIdWithLock(any());
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

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));
        when(userWalletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(userWallet));
        when(walletWithdrawalRepository.save(any(WalletWithdrawal.class))).thenAnswer(i -> i.getArgument(0));

        WalletWithdrawalReviewRequest reviewRequest = new WalletWithdrawalReviewRequest("REJECT", "Tài khoản ngân hàng sai thông tin");

        WalletWithdrawalResponse response = walletService.reviewWithdrawal(adminId, withdrawalId, reviewRequest);

        assertNotNull(response);
        assertEquals("REJECTED", response.getStatus());
        assertEquals("Tài khoản ngân hàng sai thông tin", response.getAdminNote());

        // Refunded 200,000 back to wallet (800,000 + 200,000 = 1,000,000)
        assertEquals(new BigDecimal("1000000"), userWallet.getBalance());
        verify(userWalletRepository).save(userWallet);
        verify(walletTransactionRepository).save(any(WalletTransaction.class));
        verify(notificationService).notifyWithEmail(eq(userId), contains("từ chối"), anyString(), any(NotificationType.class), eq(withdrawalId), anyString(), anyString());
    }

    @Test
    @DisplayName("reviewWithdrawal - Báo lỗi khi đơn đã được xử lý trước đó")
    void reviewWithdrawal_WhenAlreadyProcessed_ShouldThrowBadRequestException() {
        UUID withdrawalId = UUID.randomUUID();
        WalletWithdrawal withdrawal = new WalletWithdrawal(
                user, new BigDecimal("200000"), "VCB", "1234567890", "NGUYEN VAN A"
        );
        withdrawal.setStatus("APPROVED");

        when(walletWithdrawalRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(withdrawal));

        WalletWithdrawalReviewRequest reviewRequest = new WalletWithdrawalReviewRequest("REJECT", "Không duyệt lại");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> walletService.reviewWithdrawal(adminId, withdrawalId, reviewRequest));

        assertTrue(ex.getMessage().contains("đã được xử lý trước đó"));
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
        assertEquals("MB", result.getContent().get(0).getBankCode());
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
        when(walletWithdrawalRepository.findByStatusOrderByCreatedAtDesc("PENDING_REVIEW", pageable)).thenReturn(page);

        Page<WalletWithdrawalResponse> result = walletService.getAllWithdrawals("PENDING_REVIEW", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(walletWithdrawalRepository).findByStatusOrderByCreatedAtDesc("PENDING_REVIEW", pageable);
    }
}
