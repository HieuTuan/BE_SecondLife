package com.secondlife.secondlife.dto.response;

import java.util.UUID;

public record PostInitResponse(
    UUID postId,
    UUID sessionId,
    String aiInitialMessage,
    String aiDescription
) {
    public PostInitResponse(UUID postId, UUID sessionId, String aiInitialMessage) {
        this(postId, sessionId, aiInitialMessage, null);
    }
}
