package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ChatMessageDto(
    UUID id,
    UUID conversationId, // This is the roomId
    UUID senderId,
    String messageContent,
    Instant sentAt
) {
}
