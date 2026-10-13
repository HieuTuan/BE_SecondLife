package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostInitResponse {
    private UUID postId;
    private UUID sessionId;
    private String aiInitialMessage;
    private String aiDescription;

    public PostInitResponse(UUID postId, UUID sessionId, String aiInitialMessage) {
        this.postId = postId;
        this.sessionId = sessionId;
        this.aiInitialMessage = aiInitialMessage;
    }
}
