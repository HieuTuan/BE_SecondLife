package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {
    UserWallet getWalletByUserId(UUID userId);
    UserWallet createWalletForUser(UUID userId);
    void deposit(UUID userId, BigDecimal amount, UUID referenceId);
    void processPayment(UUID userId, BigDecimal amount, UUID orderId);
    void processPayment(UUID userId, BigDecimal amount, UUID referenceId, WalletTransactionReferenceType referenceType);
    void processEarning(UUID userId, BigDecimal amount, UUID orderId);
    void processEarning(UUID userId, BigDecimal amount, UUID referenceId, WalletTransactionReferenceType referenceType);
    void processRefund(UUID userId, BigDecimal amount, UUID orderId);
    void processRefund(UUID userId, BigDecimal amount, UUID referenceId, WalletTransactionReferenceType referenceType);
    BigDecimal collectReturnShippingFee(UUID sellerId, BigDecimal fee, UUID shipmentId);
    Page<WalletTransaction> getTransactions(UUID userId, Pageable pageable);

    WalletWithdrawalResponse requestWithdrawal(UUID userId, WalletWithdrawalRequest request);
    WalletWithdrawalResponse reviewWithdrawal(UUID adminId, UUID withdrawalId, WalletWithdrawalReviewRequest request);
    Page<WalletWithdrawalResponse> getMyWithdrawals(UUID userId, Pageable pageable);
    Page<WalletWithdrawalResponse> getAllWithdrawals(String status, Pageable pageable);
}
