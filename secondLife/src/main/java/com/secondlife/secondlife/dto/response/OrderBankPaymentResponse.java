package com.secondlife.secondlife.dto.response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record OrderBankPaymentResponse(UUID orderId, String status, String orderStatus, String escrowStatus,
        BigDecimal totalAmount, BigDecimal receivedAmount, BigDecimal remainingAmount, BigDecimal walletCreditedAmount,
        String transferContent, String bankAccountName, String bankAccountNumber, String bankName,
        Instant expiresAt, Instant paidAt) {}
