package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.AiChatRequest;
import com.secondlife.secondlife.dto.AiChatResponse;

import java.util.UUID;

public interface AiChatService {
    AiChatResponse processChat(AiChatRequest request, UUID currentUserId);
    String finalizeChat(UUID sessionId, UUID currentUserId);
}
