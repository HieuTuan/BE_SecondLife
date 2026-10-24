package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.SystemWalletResponse;
import com.secondlife.secondlife.dto.response.SystemWalletTransactionResponse;
import com.secondlife.secondlife.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.UUID;

public interface SystemWalletService {
    void hold(Order order);
    void release(Order order, BigDecimal platformCommission, BigDecimal sellerPayout);
    void refund(Order order, BigDecimal amount);
    BigDecimal collectReturnShippingFee(Order order, BigDecimal fee, UUID shipmentId);
    SystemWalletResponse getWallet();
    Page<SystemWalletTransactionResponse> getTransactions(Pageable pageable);
}
