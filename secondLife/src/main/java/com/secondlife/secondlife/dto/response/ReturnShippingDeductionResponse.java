package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReturnShippingDeductionResponse(UUID id, UUID orderId, BigDecimal amount, Instant deductedAt) {
}
