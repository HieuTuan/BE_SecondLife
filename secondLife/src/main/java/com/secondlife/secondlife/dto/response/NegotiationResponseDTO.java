package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.NegotiationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record NegotiationResponseDTO(
    UUID id,
    UUID postId,
    String postTitle,
    UUID buyerId,
    UUID sellerId,
    BigDecimal offeredPrice,
    NegotiationStatus status,
    Instant createdAt,
    Instant expiredAt
) {
}
