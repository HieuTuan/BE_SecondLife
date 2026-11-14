package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.InspectionFeeSettlementRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionFeeSettlementServiceImplTest {

    @Mock
    private InspectionFeeSettlementRepository settlements;

    @Mock
    private InspectionOrderRepository inspectionOrders;

    @Mock
    private WalletService walletService;

    @Mock
    private UserRepository userRepository;

    private InspectionFeeSettlementServiceImpl settlementService;

    private User buyer;
    private User seller;
    private User inspector;
    private User manager;
    private InspectionOrder order;
    private Post post;

    @BeforeEach
    void setUp() {
        settlementService = new InspectionFeeSettlementServiceImpl(
                settlements,
                inspectionOrders,
                walletService,
                userRepository,
                new BigDecimal("0.10") // 10% platform fee rate
        );

        buyer = new User();
        buyer.setId(UUID.randomUUID());

        seller = new User();
        seller.setId(UUID.randomUUID());

        manager = new User();
        manager.setId(UUID.randomUUID());

        inspector = new User();
        inspector.setId(UUID.randomUUID());
        inspector.setManager(manager);

        post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(seller);

        order = new InspectionOrder();
        order.setId(UUID.randomUUID());
        order.setPost(post);
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setInspector(inspector);
        order.setInspectionFee(new BigDecimal("100000"));
        order.setShippingFee(new BigDecimal("30000"));
        order.setReturnShippingReserveFee(new BigDecimal("30000"));
        order.setFeePayer(InspectionFeePayer.SPLIT_50_50);
        order.setBuyerFeeShare(new BigDecimal("80000"));
        order.setSellerFeeShare(new BigDecimal("80000"));
        order.setBuyerCommitmentDeposit(new BigDecimal("50000"));
        order.setBuyerPaidAt(Instant.now());
        order.setSellerPaidAt(Instant.now());

        lenient().when(settlements.saveAndFlush(any(InspectionFeeSettlement.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void newOrderUsesItsStoredRateAndDoesNotChargeCommissionOnTravel() {
        order.setPlatformFeeRateSnapshot(new BigDecimal("0.20"));
        order.setTravelFee(new BigDecimal("20000"));
        order.setShippingFee(BigDecimal.ZERO);
        order.setReturnShippingReserveFee(BigDecimal.ZERO);
        order.setBuyerCommitmentDeposit(BigDecimal.ZERO);
        order.setBuyerFeeShare(new BigDecimal("120000"));
        order.setSellerFeeShare(BigDecimal.ZERO);
        order.setInspectionCenter(manager);
        var settlement = settlementService.settle(order, InspectionSettlementOutcome.PASSED_PURCHASED, "Completed");
        assertEquals(0, new BigDecimal("100000").compareTo(settlement.getCenterPayoutAmount()));
        assertEquals(0, new BigDecimal("20000").compareTo(settlement.getPlatformRetainedAmount()));
    }

    @Test
    @DisplayName("Idempotency: nếu đơn đã hạch toán phí, trả về bản ghi cũ mà không ghi nợ/hoàn tiền lại")
    void testIdempotencyReturnsExistingSettlement() {
        InspectionFeeSettlement existing = new InspectionFeeSettlement();
        existing.setId(UUID.randomUUID());
        existing.setInspectionOrder(order);

        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(existing));

        InspectionFeeSettlement result = settlementService.settle(order, InspectionSettlementOutcome.PASSED_PURCHASED, "Test");

        assertSame(existing, result);
        verifyNoInteractions(walletService);
        verify(settlements, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @CsvSource({"BUYER_PAYS,4000,0", "SELLER_PAYS,0,4000", "SPLIT_50_50,2000,2000"})
    void returnReleasesOnlyUnusedReserveOnce(InspectionFeePayer payer, BigDecimal buyerRefund, BigDecimal sellerRefund) {
        order.setFeePayer(payer);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnReserveAllocated(new BigDecimal("36000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));

        settlementService.settleReturnShipping(order, new BigDecimal("32000"));
        settlementService.settleReturnShipping(order, new BigDecimal("32000"));

        assertEquals(0, buyerRefund.compareTo(settlement.getBuyerRefundAmount()));
        assertEquals(0, sellerRefund.compareTo(settlement.getSellerRefundAmount()));
        assertEquals(new BigDecimal("32000"), settlement.getReturnReserveAllocated());
        assertNotNull(settlement.getReturnShippingSettledAt());
        verify(walletService, times(buyerRefund.signum())).processRefund(eq(buyer.getId()), any(), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        verify(walletService, times(sellerRefund.signum())).processRefund(eq(seller.getId()), any(), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
    }

    @ParameterizedTest
    @CsvSource({"BUYER_PAYS,28000,2000", "SELLER_PAYS,0,0", "SPLIT_50_50,14000,1000"})
    void outboundReimbursesOnlyTheBuyersActualShare(InspectionFeePayer payer, BigDecimal reimbursement,
                                                   BigDecimal refund) {
        order.setCashShippingSimulation(true);
        order.setFeePayer(payer);
        var shipment = new Shipment();
        shipment.setId(UUID.randomUUID());
        shipment.setLeg("SELLER_TO_CENTER");
        shipment.setCashPayerId(seller.getId());

        var amount = settlementService.reimburseCashShipping(order, shipment, new BigDecimal("28000"));

        assertEquals(0, reimbursement.compareTo(amount));
        verify(walletService, times(reimbursement.signum())).reimburseShipping(eq(seller.getId()), any(), eq(shipment.getId()));
        verify(walletService, times(refund.signum())).processRefund(eq(buyer.getId()), any(), eq(shipment.getId()),
                eq(WalletTransactionReferenceType.SHIPMENT));
    }

    @Test
    void cashReturnCannotOverdrawTheReserve() {
        order.setCashShippingSimulation(true);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnReserveAllocated(new BigDecimal("36000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));
        var shipment = new Shipment();
        shipment.setLeg("CENTER_TO_SELLER");

        assertThrows(ConflictException.class,
                () -> settlementService.reimburseCashShipping(order, shipment, new BigDecimal("42000")));

        assertNull(settlement.getReturnShippingSettledAt());
        verifyNoInteractions(walletService);
    }

    @Test
    void automaticReturnCreditsHubOnceAndRefundsUnusedReserve() {
        order.setCashShippingSimulation(true);
        order.setInspectionCenter(manager);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnReserveAllocated(new BigDecimal("36000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));
        var shipment = new Shipment();
        shipment.setId(UUID.randomUUID());
        shipment.setLeg("CENTER_TO_SELLER");
        shipment.setCashPayerId(manager.getId());

        assertFalse(settlementService.reimburseDeliveredReturn(order, shipment, new BigDecimal("42000")));
        verifyNoInteractions(walletService);

        BigDecimal actualFee = new BigDecimal("32000");
        assertTrue(settlementService.reimburseDeliveredReturn(order, shipment, actualFee));
        assertFalse(settlementService.reimburseDeliveredReturn(order, shipment, actualFee));

        verify(walletService).reimburseShipping(manager.getId(), actualFee, shipment.getId());
        verify(walletService).processRefund(buyer.getId(), new BigDecimal("2000.00"), order.getId(),
                WalletTransactionReferenceType.INSPECTION_ORDER);
        verify(walletService).processRefund(seller.getId(), new BigDecimal("2000.00"), order.getId(),
                WalletTransactionReferenceType.INSPECTION_ORDER);
        assertNotNull(settlement.getReturnShippingSettledAt());
    }

    @Test
    void returnAboveReserveRemainsUnsettled() {
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED);
        settlement.setReturnReserveAllocated(new BigDecimal("36000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));

        settlementService.settleReturnShipping(order, new BigDecimal("42000"));

        assertNull(settlement.getReturnShippingSettledAt());
        assertEquals(new BigDecimal("36000"), settlement.getReturnReserveAllocated());
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("PAYMENT_EXPIRED: Hoàn tiền đầy đủ cho bên đã nộp, không trừ phạt hay trả phí trung tâm")
    void testPaymentExpiredRefundsAllParties() {
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.empty());

        InspectionFeeSettlement settlement = settlementService.settle(order, InspectionSettlementOutcome.PAYMENT_EXPIRED, "Expired");

        assertNotNull(settlement);
        // Buyer refund = 80000 (fee) + 50000 (deposit) = 130000
        assertEquals(new BigDecimal("130000"), settlement.getBuyerRefundAmount());
        assertEquals(new BigDecimal("80000"), settlement.getSellerRefundAmount());
        assertEquals(BigDecimal.ZERO, settlement.getCenterPayoutAmount());
        assertEquals(BigDecimal.ZERO, settlement.getSellerCompensationAmount());

        verify(walletService, times(1)).processRefund(eq(buyer.getId()), eq(new BigDecimal("130000")), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        verify(walletService, times(1)).processRefund(eq(seller.getId()), eq(new BigDecimal("80000")), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        verify(walletService, never()).processEarning(any(), any(), any(), any());
    }

    @Test
    @DisplayName("PASSED_PURCHASED: Trả phí kiểm định cho trung tâm, hoàn cọc cam kết và tiền ship hoàn dự phòng chưa dùng")
    void testPassedPurchasedSettlement() {
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.empty());

        InspectionFeeSettlement settlement = settlementService.settle(order, InspectionSettlementOutcome.PASSED_PURCHASED, "Purchased");

        assertNotNull(settlement);
        // Inspection service fee = 100000. Platform rate = 10% (10000). Center payout = 90000.
        assertEquals(new BigDecimal("90000.00"), settlement.getCenterPayoutAmount());
        assertEquals(manager, settlement.getCenterRecipientUser());

        // Return reserve = 30000, split 50/50 -> 15000 buyer, 15000 seller.
        // Buyer refund = 50000 (deposit) + 15000 (reserve refund) = 65000
        assertEquals(new BigDecimal("65000.00"), settlement.getBuyerRefundAmount());
        assertEquals(new BigDecimal("15000.00"), settlement.getSellerRefundAmount());

        verify(walletService, times(1)).processEarning(eq(manager.getId()), eq(new BigDecimal("90000.00")), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        verify(walletService, times(1)).processRefund(eq(buyer.getId()), eq(new BigDecimal("65000.00")), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        verify(walletService, times(1)).processRefund(eq(seller.getId()), eq(new BigDecimal("15000.00")), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
    }

    @Test
    @DisplayName("PASSED_BUYER_DEFAULTED: Người mua bỏ cọc -> bồi hoàn cọc cam kết cho người bán, trả phí trung tâm")
    void testPassedBuyerDefaultedCompensatesSeller() {
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.empty());

        InspectionFeeSettlement settlement = settlementService.settle(order, InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED, "Defaulted");

        assertNotNull(settlement);
        assertEquals(new BigDecimal("90000.00"), settlement.getCenterPayoutAmount());
        assertEquals(new BigDecimal("50000"), settlement.getSellerCompensationAmount());
        assertEquals(BigDecimal.ZERO, settlement.getBuyerRefundAmount());

        verify(walletService, times(1)).processEarning(eq(seller.getId()), eq(new BigDecimal("50000")), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        verify(walletService, times(1)).processEarning(eq(manager.getId()), eq(new BigDecimal("90000.00")), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
    }

    @Test
    @DisplayName("FAILED_INSPECTION: Kiểm định không đạt -> Hoàn lại cọc cam kết cho người mua, phân bổ phí ship hoàn")
    void testFailedInspectionRefundsCommitmentDeposit() {
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.empty());

        InspectionFeeSettlement settlement = settlementService.settle(order, InspectionSettlementOutcome.FAILED_INSPECTION, "Failed");

        assertNotNull(settlement);
        assertEquals(new BigDecimal("50000"), settlement.getBuyerRefundAmount());
        assertEquals(new BigDecimal("90000.00"), settlement.getCenterPayoutAmount());
        assertEquals(new BigDecimal("30000"), settlement.getReturnReserveAllocated());

        verify(walletService, times(1)).processRefund(eq(buyer.getId()), eq(new BigDecimal("50000")), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
    }
}
