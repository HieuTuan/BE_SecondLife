package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.ReturnShippingFeeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReturnShippingFeeResponse(UUID shipmentId, UUID orderId, UUID reportId, UUID sellerId,
        ReturnShippingFeeStatus status, BigDecimal feeAmount, BigDecimal collectedAmount, BigDecimal debtAmount,
        Instant createdAt, Instant assessedAt, UUID assessedBy) {
}
