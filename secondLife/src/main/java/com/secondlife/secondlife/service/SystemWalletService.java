package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.SystemWalletResponse;
import com.secondlife.secondlife.dto.response.SystemWalletTransactionResponse;
import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.SystemWalletFundCategory;
import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface SystemWalletService {
    void hold(Order order);
    void release(Order order, BigDecimal platformCommission, BigDecimal sellerPayout);
    void refund(Order order, BigDecimal amount);
    BigDecimal collectReturnShippingFee(Order order, BigDecimal fee, UUID shipmentId);
    void holdInspectionFee(InspectionOrder order, boolean buyer, BigDecimal feeShare, BigDecimal deposit);
    boolean lockInspectionFunds(UUID inspectionOrderId);
    void settleInspectionFees(InspectionFeeSettlement settlement);
    void settleInspectionReturn(UUID inspectionOrderId, BigDecimal carrierFee, BigDecimal sellerCollected,
                                BigDecimal reserveRefund);
    SystemWalletResponse getWallet();
    Page<SystemWalletTransactionResponse> getTransactions(
            SystemWalletTransactionType type, Boolean hasShipping, UUID orderId, SystemWalletFundCategory fundCategory, Pageable pageable);
}
