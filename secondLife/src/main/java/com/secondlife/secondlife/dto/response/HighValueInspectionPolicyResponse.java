package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HighValueInspectionPolicyResponse(
        BigDecimal thresholdPrice, String currency, long version, Instant updatedAt, UUID updatedBy) {
}
