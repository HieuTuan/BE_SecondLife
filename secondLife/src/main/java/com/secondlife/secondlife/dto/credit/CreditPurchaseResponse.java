package com.secondlife.secondlife.dto.credit;

import com.secondlife.secondlife.enums.CreditPurchaseStatus;
import com.secondlife.secondlife.service.payment.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record CreditPurchaseResponse(
        UUID id,
        CreditPurchaseStatus status,
        CreditQuoteResponse snapshot,
        UUID paymentIntentId,
        String paymentProvider,
        String providerIntentId,
        PaymentStatus paymentStatus,
        Instant createdAt,
        Instant paidAt) {
}
