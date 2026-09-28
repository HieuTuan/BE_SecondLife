package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.dto.response.PostFinalizeResponse;

import java.util.UUID;

public interface AiChatService {
    AiChatResponse processChat(AiChatRequest request, UUID currentUserId);
    PostFinalizeResponse finalizeChat(UUID sessionId, UUID currentUserId);
}
