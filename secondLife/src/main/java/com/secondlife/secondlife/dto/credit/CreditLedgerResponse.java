package com.secondlife.secondlife.dto.credit;

import com.secondlife.secondlife.enums.CreditType;

import java.time.Instant;
import java.util.UUID;

public record CreditLedgerResponse(
        UUID id,
        CreditType creditType,
        UUID purchaseId,
        String entryType,
        long quantityDelta,
        long balanceAfter,
        Instant createdAt) {
}
