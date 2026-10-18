package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.CreditPurchaseStatus;
import com.secondlife.secondlife.enums.PaymentStatus;

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
