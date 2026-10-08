package com.secondlife.secondlife.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ChatMessageDto {
    private UUID id;
    private UUID conversationId; // This is the roomId
    private UUID senderId;
    private String messageContent;
    private Instant sentAt;
}
