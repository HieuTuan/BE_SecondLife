package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.UserWallet;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {
    UserWallet getWalletByUserId(UUID userId);
    UserWallet createWalletForUser(UUID userId);
    void deposit(UUID userId, BigDecimal amount, UUID referenceId);
    void processPayment(UUID userId, BigDecimal amount, UUID orderId);
    void processEarning(UUID userId, BigDecimal amount, UUID orderId);
    void processRefund(UUID userId, BigDecimal amount, UUID orderId);
    org.springframework.data.domain.Page<com.secondlife.secondlife.entity.WalletTransaction> getTransactions(UUID userId, org.springframework.data.domain.Pageable pageable);
}
