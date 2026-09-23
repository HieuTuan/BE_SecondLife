package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;

import java.util.UUID;

public interface AiChatService {
    AiChatResponse processChat(AiChatRequest request, UUID currentUserId);
    String finalizeChat(UUID sessionId, UUID currentUserId);
}
