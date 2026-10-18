package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderConfirmationResponse(UUID orderId, UUID postId, String postTitle, UUID buyerId,
        UUID sellerId, OrderStatus status, String confirmationStatus, Instant paidAt,
        Instant confirmationDeadline, Instant confirmationCancelAt, Instant sellerConfirmedAt,
        @Schema(description="Points snapshotted at payment; applied once if the first deadline is missed") BigDecimal latePenaltyPoints,
        @Schema(description="Additional snapshotted points applied once on confirmation timeout cancellation") BigDecimal timeoutPenaltyPoints) {
}
