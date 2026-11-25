package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InspectionReturnShippingPolicyHistoryResponse(
        Long id,
        BigDecimal previousMarkupPercentage,
        BigDecimal newMarkupPercentage,
        Instant changedAt,
        UUID changedBy
) {
}
