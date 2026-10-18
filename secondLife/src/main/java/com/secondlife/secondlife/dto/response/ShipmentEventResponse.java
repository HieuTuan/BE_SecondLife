package com.secondlife.secondlife.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ShipmentEventResponse(
    UUID id,
    UUID shipmentId,
    String eventKey,
    String type,
    String status,
    Instant occurredAt,
    String reason,
    String payload
) {
}
