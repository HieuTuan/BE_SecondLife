package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;

public interface SystemWalletService {
    void hold(Order order);
    void release(Order order, BigDecimal platformCommission, BigDecimal sellerPayout);
    void refund(Order order, BigDecimal amount);
    SystemWalletResponse getWallet();
    Page<SystemWalletTransactionResponse> getTransactions(Pageable pageable);
}
