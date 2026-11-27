package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.InspectionFeeSettlementRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.SystemWalletService;
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
import static org.mockito.ArgumentMatchers.argThat;
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
    private SystemWalletService systemWallet;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notifications;

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
                systemWallet,
                userRepository,
                notifications,
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

    @ParameterizedTest
    @CsvSource({
            "BUYER_PAYS,160000,0,30000,0",
            "SELLER_PAYS,0,160000,0,30000",
            "SPLIT_50_50,80000,80000,15000,15000"
    })
    void corporatePurchasedRefundsReserveAccordingToFeePayer(InspectionFeePayer payer, BigDecimal buyerFee,
                                                             BigDecimal sellerFee, BigDecimal expectedBuyerRefund,
                                                             BigDecimal expectedSellerRefund) {
        order.setCorporateShipping(true);
        order.setFeePayer(payer);
        order.setBuyerFeeShare(buyerFee);
        order.setSellerFeeShare(sellerFee);
        order.setBuyerCommitmentDeposit(BigDecimal.ZERO);

        var settlement = settlementService.settle(order, InspectionSettlementOutcome.PASSED_PURCHASED, "Test");

        assertEquals(0, new BigDecimal("30000").compareTo(settlement.getOutboundShippingAllocated()));
        assertEquals(0, expectedBuyerRefund.compareTo(settlement.getBuyerRefundAmount()));
        assertEquals(0, expectedSellerRefund.compareTo(settlement.getSellerRefundAmount()));
        if (expectedBuyerRefund.signum() > 0) {
            verify(walletService).processRefund(eq(buyer.getId()), any(), eq(order.getId()),
                    eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        }
        if (expectedSellerRefund.signum() > 0) {
            verify(walletService).processRefund(eq(seller.getId()), any(), eq(order.getId()),
                    eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0,528260", "35,184891,343369", "65,343369,184891", "100,528260,0"})
    void failedCorporateInspectionKeepsAgreedFeesAndHoldsReturnReserve(
            int buyerPercent, BigDecimal buyerFee, BigDecimal sellerFee) {
        order.setCorporateShipping(true);
        order.setFeePayer(InspectionFeePayer.CUSTOM);
        order.setBuyerFeePercent(buyerPercent);
        order.setInspectionFee(new BigDecimal("290000"));
        order.setShippingFee(new BigDecimal("108300"));
        order.setReturnShippingReserveFee(new BigDecimal("129960"));
        order.setPlatformFeeRateSnapshot(BigDecimal.ZERO);
        order.setBuyerCommitmentDeposit(BigDecimal.ZERO);
        order.setBuyerFeeShare(buyerFee);
        order.setSellerFeeShare(sellerFee);
        when(systemWallet.lockInspectionFunds(order.getId())).thenReturn(true);

        var settlement = settlementService.settle(order, InspectionSettlementOutcome.FAILED_INSPECTION, "Failed");

        assertEquals(new BigDecimal("528260"), settlement.getTotalCollected());
        assertEquals(new BigDecimal("129960"), settlement.getReturnReserveAllocated());
        assertEquals(BigDecimal.ZERO, settlement.getBuyerRefundAmount());
        assertEquals(BigDecimal.ZERO, settlement.getSellerRefundAmount());
        assertEquals(BigDecimal.ZERO, settlement.getSellerAdditionalCollected());
        assertEquals(BigDecimal.ZERO, settlement.getSellerOutstandingAmount());
        assertNull(settlement.getReturnShippingSettledAt());
        assertEquals(buyerFee, order.getBuyerFeeShare());
        assertEquals(sellerFee, order.getSellerFeeShare());
        verify(walletService).processEarning(manager.getId(), new BigDecimal("290000.00"), order.getId(),
                WalletTransactionReferenceType.INSPECTION_ORDER);
        verifyNoMoreInteractions(walletService);
        verify(systemWallet).settleInspectionFees(settlement);
    }

    @ParameterizedTest
    @CsvSource({"0,129960,108300,0,21660", "35,129960,108300,7581,14079",
            "50,129960,108300,10830,10830", "100,129960,108300,21660,0",
            "35,129960.02,108300.01,7581.00,14079.01"})
    void failedCorporateReturnRefundsOnlySurplusAtTheAgreedPercentageOnce(
            int buyerPercent, BigDecimal reserve, BigDecimal actualFee,
            BigDecimal buyerRefund, BigDecimal sellerRefund) {
        order.setCorporateShipping(true);
        order.setFeePayer(InspectionFeePayer.CUSTOM);
        order.setBuyerFeePercent(buyerPercent);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnReserveAllocated(reserve);
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));
        when(systemWallet.lockInspectionFunds(order.getId())).thenReturn(true);

        settlementService.settleReturnShipping(order, actualFee);
        settlementService.settleReturnShipping(order, actualFee);

        assertEquals(0, buyerRefund.compareTo(settlement.getBuyerRefundAmount()));
        assertEquals(0, sellerRefund.compareTo(settlement.getSellerRefundAmount()));
        assertEquals(actualFee, settlement.getReturnReserveAllocated());
        assertNotNull(settlement.getReturnShippingSettledAt());
        verify(walletService, times(buyerRefund.signum())).processRefund(eq(buyer.getId()), any(), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        verify(walletService, times(sellerRefund.signum())).processRefund(eq(seller.getId()), any(), eq(order.getId()),
                eq(WalletTransactionReferenceType.INSPECTION_ORDER));
        verifyNoMoreInteractions(walletService);
        verify(systemWallet).settleInspectionReturn(eq(order.getId()), eq(actualFee), eq(BigDecimal.ZERO),
                argThat(amount -> amount.compareTo(reserve.subtract(actualFee)) == 0));
    }

    @Test
    void preListingCorporateReturnAboveReserveRemainsUnsettledWithoutChargingSeller() {
        order.setCorporateShipping(true);
        order.setInspectionScope(InspectionScope.PRE_LISTING);
        order.setBuyer(null);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.PRE_LISTING_PASSED);
        settlement.setReturnReserveAllocated(new BigDecimal("30000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));

        settlementService.settleReturnShipping(order, new BigDecimal("40000"));

        assertNull(settlement.getReturnShippingSettledAt());
        assertEquals(new BigDecimal("30000"), settlement.getReturnReserveAllocated());
        verifyNoInteractions(walletService);
    }

    @Test
    void legacySellerPaidReturnOverageCollectsAvailableFundsAndRecordsRemainingDebtOnce() {
        order.setCorporateShipping(true);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnReserveAllocated(new BigDecimal("30000"));
        settlement.setSellerOutstandingAmount(new BigDecimal("5000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));
        when(walletService.collectFailedInspectionFee(seller.getId(), new BigDecimal("10000"), order.getId()))
                .thenReturn(new BigDecimal("2000"));

        settlementService.settleReturnShipping(order, new BigDecimal("40000"));
        settlementService.settleReturnShipping(order, new BigDecimal("40000"));

        assertEquals(new BigDecimal("40000"), settlement.getReturnReserveAllocated());
        assertEquals(new BigDecimal("2000"), settlement.getSellerAdditionalCollected());
        assertEquals(new BigDecimal("13000"), settlement.getSellerOutstandingAmount());
        verify(walletService).collectFailedInspectionFee(seller.getId(), new BigDecimal("10000"), order.getId());
        verifyNoMoreInteractions(walletService);
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
    void cashReturnAboveReserveRemainsUnsettled() {
        order.setCashShippingSimulation(true);
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
    void preListingSuccessPaysCenterAndKeepsReturnReserveForSellerWithoutABuyer() {
        order.setBuyer(null);
        order.setBuyerPaidAt(null);
        order.setBuyerFeeShare(BigDecimal.ZERO);
        order.setBuyerCommitmentDeposit(BigDecimal.ZERO);
        order.setFeePayer(InspectionFeePayer.SELLER_PAYS);
        order.setBuyerFeePercent(0);
        order.setSellerFeeShare(new BigDecimal("160000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.empty());

        var result = settlementService.settle(order, InspectionSettlementOutcome.PRE_LISTING_PASSED, "Passed");

        assertEquals(new BigDecimal("90000.00"), result.getCenterPayoutAmount());
        assertEquals(new BigDecimal("30000"), result.getOutboundShippingAllocated());
        assertEquals(new BigDecimal("30000"), result.getReturnReserveAllocated());
        assertEquals(BigDecimal.ZERO, result.getBuyerRefundAmount());
        assertEquals(BigDecimal.ZERO, result.getSellerRefundAmount());
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
    void completedPurchaseRefundsUnusedReserveAtTheAgreedPercentage() {
        order.setFeePayer(InspectionFeePayer.CUSTOM);
        order.setBuyerFeePercent(55);
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.empty());

        InspectionFeeSettlement settlement =
                settlementService.settle(order, InspectionSettlementOutcome.PASSED_PURCHASED, "Purchased");

        assertEquals(new BigDecimal("66500.00"), settlement.getBuyerRefundAmount());
        assertEquals(new BigDecimal("13500.00"), settlement.getSellerRefundAmount());
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

    @Test
    void chargeReturnQuoteCollectsSplitAndHandlesPartialWalletAndNotification() {
        order.setBuyerFeePercent(65);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnReserveAllocated(new BigDecimal("36000"));
        var shipment = new Shipment();
        shipment.setId(UUID.randomUUID());
        shipment.setQuotedFee(new BigDecimal("45000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));
        when(systemWallet.lockInspectionFunds(order.getId())).thenReturn(true);
        when(walletService.collectInspectionReturnFee(buyer.getId(), new BigDecimal("5850.00"), order.getId()))
                .thenReturn(new BigDecimal("5850.00"));
        when(walletService.collectInspectionReturnFee(seller.getId(), new BigDecimal("3150.00"), order.getId()))
                .thenReturn(new BigDecimal("1000.00"));

        settlementService.chargeReturnQuote(order, shipment);
        // Calling twice for the same shipment does not double charge
        settlementService.chargeReturnQuote(order, shipment);

        assertEquals(0, new BigDecimal("5850.00").compareTo(settlement.getReturnBuyerCollected()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(settlement.getReturnSellerCollected()));
        verify(systemWallet, times(1)).recordInspectionReturnCollection(order.getId(), new BigDecimal("6850.00"), false);
        verify(notifications).notifyInApp(eq(buyer.getId()), any(), contains("65%"), eq(NotificationType.SHIPPING),
                eq(order.getId()), eq("INSPECTION_ORDER"), eq("/buyer/inspections/" + order.getId() + "/decision"));
        verify(notifications).notifyInApp(eq(seller.getId()), any(), contains("35%"), eq(NotificationType.SHIPPING),
                eq(order.getId()), eq("INSPECTION_ORDER"), eq("/seller/inspections/" + order.getId() + "/decision"));
    }

    @Test
    void cancelledQuoteClearsDebtAndCreditsCollectedFundsToRebookedShipment() {
        order.setBuyerFeePercent(65);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnReserveAllocated(new BigDecimal("36000"));
        var first = new Shipment();
        first.setId(UUID.randomUUID());
        first.setQuotedFee(new BigDecimal("45000"));
        var second = new Shipment();
        second.setId(UUID.randomUUID());
        second.setQuotedFee(new BigDecimal("50000"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));
        when(systemWallet.lockInspectionFunds(order.getId())).thenReturn(true);
        when(walletService.collectInspectionReturnFee(buyer.getId(), new BigDecimal("5850.00"), order.getId()))
                .thenReturn(new BigDecimal("5850.00"));
        when(walletService.collectInspectionReturnFee(seller.getId(), new BigDecimal("3150.00"), order.getId()))
                .thenReturn(BigDecimal.ZERO);

        settlementService.chargeReturnQuote(order, first);
        settlementService.clearCancelledReturnQuote(order, first);
        when(inspectionOrders.findById(order.getId())).thenReturn(Optional.of(order));
        var cancelledView = settlementService.getReturnFee(buyer.getId(), false, order.getId());
        assertNull(cancelledView.quotedFee());
        assertEquals(0, cancelledView.buyer().outstanding().signum());
        assertEquals(0, cancelledView.seller().outstanding().signum());

        // Rebooked at 50,000 -> total shortfall 14,000 -> buyer target 9,100 (already paid 5,850 -> owes 3,250)
        when(walletService.collectInspectionReturnFee(buyer.getId(), new BigDecimal("3250.00"), order.getId()))
                .thenReturn(new BigDecimal("3250.00"));
        when(walletService.collectInspectionReturnFee(seller.getId(), new BigDecimal("4900.00"), order.getId()))
                .thenReturn(new BigDecimal("4900.00"));
        settlementService.chargeReturnQuote(order, second);

        assertEquals(0, new BigDecimal("9100.00").compareTo(settlement.getReturnBuyerCollected()));
        assertEquals(0, new BigDecimal("4900.00").compareTo(settlement.getReturnSellerCollected()));
    }

    @Test
    void deliveryReconcilesIncreasedFeeAndExcessReserveAndSupportsManualDebtPayment() {
        order.setBuyerFeePercent(65);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnOriginalReserve(new BigDecimal("36000"));
        settlement.setReturnReserveAllocated(new BigDecimal("36000"));
        settlement.setReturnQuoteFee(new BigDecimal("45000"));
        settlement.setReturnBuyerCollected(new BigDecimal("5850.00"));
        settlement.setReturnSellerCollected(BigDecimal.ZERO);
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));
        when(inspectionOrders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(systemWallet.lockInspectionFunds(order.getId())).thenReturn(true);

        // Actual fee increases to 48,000 (shortfall 12,000 -> buyer 7,800, seller 4,200). Seller still lacks balance.
        when(walletService.collectInspectionReturnFee(buyer.getId(), new BigDecimal("1950.00"), order.getId()))
                .thenReturn(new BigDecimal("1950.00"));
        when(walletService.collectInspectionReturnFee(seller.getId(), new BigDecimal("4200.00"), order.getId()))
                .thenReturn(BigDecimal.ZERO);

        settlementService.settleReturnShipping(order, new BigDecimal("48000"));
        // Duplicate settlement call is a no-op
        settlementService.settleReturnShipping(order, new BigDecimal("48000"));

        assertNotNull(settlement.getReturnShippingSettledAt());
        verify(systemWallet, times(1)).settleInspectionReturn(
                eq(order.getId()),
                eq(new BigDecimal("48000")),
                argThat(amount -> amount.signum() == 0),
                argThat(amount -> amount.signum() == 0));

        // Seller pays debt manually; stale maxAmount is rejected
        assertThrows(ConflictException.class, () -> settlementService.payReturnFee(seller.getId(), order.getId(), new BigDecimal("4000")));
        when(walletService.collectInspectionReturnFee(seller.getId(), new BigDecimal("4200.00"), order.getId()))
                .thenReturn(new BigDecimal("4200.00"));
        var afterDebt = settlementService.payReturnFee(seller.getId(), order.getId(), new BigDecimal("4200.00"));
        assertEquals(0, afterDebt.seller().outstanding().signum());
        verify(systemWallet).recordInspectionReturnCollection(order.getId(), new BigDecimal("4200.00"), true);
    }

    @Test
    void actualFeeBelowReserveRefundsBothOverpaidTopUpAndUnusedReserve() {
        order.setBuyerFeePercent(65);
        var settlement = new InspectionFeeSettlement();
        settlement.setOutcome(InspectionSettlementOutcome.FAILED_INSPECTION);
        settlement.setReturnOriginalReserve(new BigDecimal("36000"));
        settlement.setReturnReserveAllocated(new BigDecimal("36000"));
        settlement.setReturnQuoteFee(new BigDecimal("45000"));
        settlement.setReturnBuyerCollected(new BigDecimal("5850.00"));
        settlement.setReturnSellerCollected(new BigDecimal("3150.00"));
        when(settlements.findByInspectionOrderId(order.getId())).thenReturn(Optional.of(settlement));
        when(systemWallet.lockInspectionFunds(order.getId())).thenReturn(true);

        // Actual fee is 30,000 (< 36,000 reserve): refund 6,000 reserve + 9,000 top-up = 15,000 total (65% = 9,750, 35% = 5,250)
        settlementService.settleReturnShipping(order, new BigDecimal("30000"));

        assertEquals(0, new BigDecimal("9750.00").compareTo(settlement.getBuyerRefundAmount()));
        assertEquals(0, new BigDecimal("5250.00").compareTo(settlement.getSellerRefundAmount()));
        verify(walletService).processRefund(buyer.getId(), new BigDecimal("9750.00"), order.getId(), WalletTransactionReferenceType.INSPECTION_ORDER);
        verify(walletService).processRefund(seller.getId(), new BigDecimal("5250.00"), order.getId(), WalletTransactionReferenceType.INSPECTION_ORDER);
    }
}
