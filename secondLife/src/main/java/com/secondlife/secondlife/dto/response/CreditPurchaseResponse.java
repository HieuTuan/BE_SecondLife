package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.CreditPurchaseStatus;

import java.time.Instant;
import java.util.UUID;

public record CreditPurchaseResponse(
        UUID id,
        CreditPurchaseStatus status,
        CreditQuoteResponse snapshot,
        Instant createdAt,
        Instant paidAt) {
}
