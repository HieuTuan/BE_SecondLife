package com.secondlife.secondlife.dto.response;

import java.time.Instant;
import java.util.UUID;

public record OrderReturnResponse(UUID orderId, UUID reportId, UUID shipmentId, String shipmentStatus,
        String orderCode, Instant refundedAt) { }
