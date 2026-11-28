package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.WalletWithdrawalRequest;
import com.secondlife.secondlife.dto.response.WalletWithdrawalResponse;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;
import com.secondlife.secondlife.dto.response.ReportEvidenceContent;
import com.secondlife.secondlife.dto.response.WalletWithdrawalStatusHistoryResponse;
import com.secondlife.secondlife.dto.request.WalletWithdrawalReviewRequest;

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
    void reimburseShipping(UUID userId, BigDecimal amount, UUID shipmentId);
    BigDecimal collectFailedInspectionFee(UUID sellerId, BigDecimal fee, UUID inspectionOrderId);
    BigDecimal collectReturnShippingFee(UUID sellerId, BigDecimal fee, UUID shipmentId);
    BigDecimal collectInspectionReturnFee(UUID userId, BigDecimal fee, UUID inspectionOrderId);
    Page<WalletTransaction> getTransactions(UUID userId, Pageable pageable);

    WalletWithdrawalResponse requestWithdrawal(UUID userId, WalletWithdrawalRequest request);
    WalletWithdrawalResponse requestWithdrawal(UUID userId, WalletWithdrawalRequest request, String idempotencyKey, boolean bankInfoConfirmed);
    WalletWithdrawalResponse requestWithdrawal(UUID userId, WalletWithdrawalRequest request, String idempotencyKey, boolean bankInfoConfirmed, String warningVersion);
    WalletWithdrawalResponse reviewWithdrawal(UUID adminId, UUID withdrawalId, WalletWithdrawalReviewRequest request);
    WalletWithdrawalResponse reportWithdrawalIssue(UUID userId, UUID withdrawalId, String type, String description);
    WalletWithdrawalResponse reportWithdrawalIssue(UUID userId, UUID withdrawalId, String type, String description, java.util.List<String> evidenceUrls);
    WalletWithdrawalResponse requestWithdrawalException(UUID userId, UUID withdrawalId, String reason, java.util.List<String> evidenceUrls);
    WalletWithdrawalResponse cancelWithdrawal(UUID userId, UUID withdrawalId, String reason);
    WalletWithdrawalResponse confirmReceipt(UUID userId, UUID id);
    int completeExpiredWithdrawals();
    WalletWithdrawalResponse uploadWithdrawalEvidence(UUID staffId, UUID withdrawalId, MultipartFile file);
    ReportEvidenceContent readWithdrawalEvidence(UUID actorId, boolean staff, UUID withdrawalId);
    WalletWithdrawalResponse getMyWithdrawal(UUID userId, UUID withdrawalId);
    java.util.List<WalletWithdrawalStatusHistoryResponse> getMyWithdrawalStatusHistory(UUID userId, UUID withdrawalId);
    java.util.List<WalletWithdrawalStatusHistoryResponse> getAdminWithdrawalStatusHistory(UUID withdrawalId);
    Page<WalletWithdrawalResponse> getMyWithdrawals(UUID userId, Pageable pageable);
    Page<WalletWithdrawalResponse> getAllWithdrawals(String status, Pageable pageable);
}
