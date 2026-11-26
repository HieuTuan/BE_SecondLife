package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.SystemWallet;
import com.secondlife.secondlife.entity.SystemWalletTransaction;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.mapper.SystemWalletMapper;
import com.secondlife.secondlife.repository.SystemOrderFundsRepository;
import com.secondlife.secondlife.repository.SystemWalletRepository;
import com.secondlife.secondlife.repository.SystemWalletStatementRepository;
import com.secondlife.secondlife.repository.SystemWalletTransactionRepository;
import com.secondlife.secondlife.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemWalletServiceImplTest {

    @Mock
    private SystemWalletRepository wallets;
    @Mock
    private SystemOrderFundsRepository funds;
    @Mock
    private SystemWalletTransactionRepository transactions;
    @Mock
    private SystemWalletStatementRepository statement;
    @Mock
    private SystemWalletMapper systemWalletMapper;
    @Mock
    private WalletService userWallets;

    @InjectMocks
    private SystemWalletServiceImpl service;

    private SystemWallet wallet;
    private UUID inspectionId;
    private InspectionOrder order;

    @BeforeEach
    void setUp() {
        wallet = new SystemWallet();
        wallet.setId(SystemWallet.MAIN);
        wallet.setHeldBalance(new BigDecimal("15000.00"));
        wallet.setShippingBalance(new BigDecimal("114660.00"));
        wallet.setRevenueBalance(BigDecimal.ZERO);

        inspectionId = UUID.randomUUID();
        order = new InspectionOrder();
        order.setId(inspectionId);
        order.setReturnShippingReserveFee(new BigDecimal("60060.00"));

        when(wallets.findByIdForUpdate(SystemWallet.MAIN)).thenReturn(Optional.of(wallet));
        when(transactions.save(any(SystemWalletTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void settleInspectionFeesKeepsOutboundShippingInsideShippingBalanceAndRefundsReserve() {
        var balances = mock(SystemWalletTransactionRepository.InspectionBalances.class);
        when(balances.getHeld()).thenReturn(new BigDecimal("15000.00"));
        when(balances.getShipping()).thenReturn(new BigDecimal("114660.00"));
        when(transactions.getInspectionBalances(inspectionId)).thenReturn(balances);

        var settlement = new InspectionFeeSettlement();
        settlement.setInspectionOrder(order);
        settlement.setOutcome(InspectionSettlementOutcome.PASSED_PURCHASED);
        settlement.setCenterPayoutAmount(new BigDecimal("15000.00"));
        settlement.setBuyerRefundAmount(new BigDecimal("30030.00"));
        settlement.setSellerRefundAmount(new BigDecimal("30030.00"));
        settlement.setSellerCompensationAmount(BigDecimal.ZERO);
        settlement.setPlatformRetainedAmount(BigDecimal.ZERO);
        settlement.setSellerAdditionalCollected(BigDecimal.ZERO);
        settlement.setOutboundShippingAllocated(new BigDecimal("54600.00"));
        settlement.setReturnReserveAllocated(BigDecimal.ZERO);

        service.settleInspectionFees(settlement);

        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getHeldBalance()));
        // Only unused reserve 60,060 is refunded; outbound shipping 54,600 stays in shippingBalance ("Phí vận chuyển đã thu").
        assertEquals(0, new BigDecimal("54600.00").compareTo(wallet.getShippingBalance()));
    }

    @Test
    void settleInspectionReturnAndDebtPaymentKeepCarrierShippingInShippingBalanceAndRefundOnlyExcess() {
        wallet.setHeldBalance(BigDecimal.ZERO);
        wallet.setShippingBalance(new BigDecimal("99600.00")); // 54,600 outbound + 36,000 reserve + 9,000 top-up

        var balances = mock(SystemWalletTransactionRepository.InspectionBalances.class);
        when(balances.getHeld()).thenReturn(BigDecimal.ZERO);
        when(balances.getShipping()).thenReturn(new BigDecimal("99600.00"));
        when(transactions.getInspectionBalances(inspectionId)).thenReturn(balances);
        when(transactions.getSettledInspectionOutboundShipping(inspectionId)).thenReturn(new BigDecimal("54600.00"));

        // Actual fee 40,000 -> refund 5,000 excess back to users, keep 40,000 + 54,600 inside shippingBalance.
        service.settleInspectionReturn(inspectionId, new BigDecimal("40000.00"), BigDecimal.ZERO, new BigDecimal("5000.00"));

        assertEquals(0, new BigDecimal("94600.00").compareTo(wallet.getShippingBalance()));

        // Subsequent manual debt payment adds to shippingBalance without being wiped out.
        service.recordInspectionReturnCollection(inspectionId, new BigDecimal("4200.00"), true);
        assertEquals(0, new BigDecimal("98800.00").compareTo(wallet.getShippingBalance()));
        verify(wallets, atLeastOnce()).save(wallet);
    }
}
