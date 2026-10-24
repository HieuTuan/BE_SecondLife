package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record InspectionSealLogResponse(
    UUID id,
    String tamperSealId,
    UUID scannedBy,
    String scannedByName,
    String scanStage,
    String sealCondition,
    String locationText,
    BigDecimal latitude,
    BigDecimal longitude,
    String note,
    Instant scannedAt
) {
}
