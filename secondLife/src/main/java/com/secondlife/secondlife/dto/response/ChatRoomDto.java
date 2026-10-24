package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ChatRoomDto(
    UUID id,
    UUID postId,
    String postTitle,
    String postImageUrl,
    UUID buyerId,
    String buyerName,
    String buyerAvatar,
    UUID sellerId,
    String sellerName,
    String sellerAvatar,
    String lastMessage,
    Instant updatedAt
) {
}
