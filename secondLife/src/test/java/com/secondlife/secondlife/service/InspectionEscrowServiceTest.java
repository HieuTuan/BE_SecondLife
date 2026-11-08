package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.InspectionEscrowServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionEscrowServiceTest {

    @Mock
    private InspectionOrderRepository inspectionOrderRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private InspectionOrderMapper inspectionOrderMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InspectionFeeSettlementService feeSettlementService;

    @InjectMocks
    private InspectionEscrowServiceImpl escrowService;

    private UUID buyerId;
    private UUID sellerId;
    private UUID orderId;
    private User buyer;
    private User seller;
    private InspectionOrder order;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        buyer = new User();
        buyer.setId(buyerId);

        seller = new User();
        seller.setId(sellerId);

        order = new InspectionOrder();
        order.setId(orderId);
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setOrderType(InspectionOrderType.AT_HOME);
        order.setFeePayer(InspectionFeePayer.SPLIT_50_50);
        order.setStatus(InspectionOrderStatus.WAITING_FEE_PAYMENT.name());
        order.setBuyerFeeShare(BigDecimal.valueOf(100000));
        order.setSellerFeeShare(BigDecimal.valueOf(100000));
        order.setPaymentDeadline(Instant.now().plus(6, ChronoUnit.HOURS));
    }

    @Test
    void fundedOrderNotifiesOnlyItsSelectedCenter() {
        User center = new User();
        center.setId(UUID.randomUUID());
        order.setInspectionCenter(center);
        order.setBuyerPaidAt(Instant.now());
        when(inspectionOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any(InspectionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        escrowService.payInspectionFee(sellerId, orderId);

        verify(notificationService).notify(eq(center.getId()), anyString(), anyString(), eq(NotificationType.INSPECTION),
                eq(orderId), eq("INSPECTION_ORDER"), anyString());
        verifyNoMoreInteractions(notificationService);
        verify(userRepository, never()).findActiveInspectionCenterUserIds();
    }

    @Test
    void sellerPaysServiceButBuyerMustStillFundTheirCommitmentDeposit() {
        order.setBuyerFeeShare(BigDecimal.ZERO);
        order.setBuyerCommitmentDeposit(new BigDecimal("5000"));
        when(inspectionOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any(InspectionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        escrowService.payInspectionFee(sellerId, orderId);
        assertEquals("WAITING_FEE_PAYMENT", order.getStatus());
        escrowService.payInspectionFee(buyerId, orderId);
        assertEquals("CONFIRMED_PENDING_DISPATCH", order.getStatus());
    }

    @Test
    @DisplayName("Buyer trả trước 50%: Chưa chuyển trạng thái, ghi nhận buyerPaidAt")
    void testBuyerPaysFirst() {
        when(inspectionOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any(InspectionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionOrder updated = escrowService.payInspectionFee(buyerId, orderId);

        assertNotNull(updated.getBuyerPaidAt());
        assertNull(updated.getSellerPaidAt());
        assertEquals(InspectionOrderStatus.WAITING_FEE_PAYMENT.name(), updated.getStatus());
        verify(walletService, times(1)).processPayment(buyerId, BigDecimal.valueOf(100000), orderId,
                WalletTransactionReferenceType.INSPECTION_ORDER);
    }

    @Test
    @DisplayName("Seller trả nốt 50%: Cả 2 bên đủ tiền -> chuyển CONFIRMED_PENDING_DISPATCH")
    void testBothPaidCompletesEscrow() {
        order.setBuyerPaidAt(Instant.now()); // Buyer đã nộp trước
        when(inspectionOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any(InspectionOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InspectionOrder updated = escrowService.payInspectionFee(sellerId, orderId);

        assertNotNull(updated.getBuyerPaidAt());
        assertNotNull(updated.getSellerPaidAt());
        assertEquals(InspectionOrderStatus.CONFIRMED_PENDING_DISPATCH.name(), updated.getStatus());
        verify(walletService, times(1)).processPayment(sellerId, BigDecimal.valueOf(100000), orderId,
                WalletTransactionReferenceType.INSPECTION_ORDER);
    }

    @Test
    @DisplayName("Quá hạn 6 tiếng: Nộp tiền thất bại và kích hoạt hoàn tiền tự động qua feeSettlementService")
    void testPaymentDeadlineExpired() {
        order.setPaymentDeadline(Instant.now().minus(1, ChronoUnit.MINUTES));
        order.setBuyerPaidAt(Instant.now().minus(2, ChronoUnit.HOURS)); // Buyer đã nộp trước
        when(inspectionOrderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThrows(ConflictException.class, () -> escrowService.payInspectionFee(sellerId, orderId));

        verify(feeSettlementService, times(1)).settle(eq(order), eq(InspectionSettlementOutcome.PAYMENT_EXPIRED), any());
        assertEquals(InspectionOrderStatus.PAYMENT_EXPIRED.name(), order.getStatus());
    }

    @Test
    @DisplayName("refundExpiredOrder: Hoàn trả tiền qua feeSettlementService và mở lại post về ACTIVE")
    void testRefundExpiredOrder() {
        order.setBuyerPaidAt(Instant.now().minus(2, ChronoUnit.HOURS));
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setStatus("RESERVED");
        order.setPost(post);

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        escrowService.refundExpiredOrder(order);

        verify(feeSettlementService, times(1)).settle(eq(order), eq(InspectionSettlementOutcome.PAYMENT_EXPIRED), any());
        assertEquals(InspectionOrderStatus.PAYMENT_EXPIRED.name(), order.getStatus());
        assertEquals("ACTIVE", post.getStatus());
        verify(postRepository, times(1)).save(post);
    }
}
