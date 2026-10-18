package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AiChatResponse(
    UUID sessionId,
    String reply
) {
}
