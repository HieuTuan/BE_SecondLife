package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MainFlowDescriptionTest {
    @Test
    void finalizingDescriptionDoesNotPerformUnchargedValuation() {
        ChatModel ollama = mock(ChatModel.class);
        ChatModel google = mock(ChatModel.class);
        when(ollama.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
        AiChatSessionRepository sessions = mock(AiChatSessionRepository.class);
        AiChatMessageRepository messages = mock(AiChatMessageRepository.class);
        UserRepository users = mock(UserRepository.class);
        PostRepository posts = mock(PostRepository.class);
        CreditService legacyCredits = mock(CreditService.class);
        User owner = new User(); owner.setId(UUID.randomUUID());
        when(users.findByIdForRoleUpdate(owner.getId())).thenReturn(Optional.of(owner));
        AiChatSession session = new AiChatSession();
        session.setId(UUID.randomUUID()); session.setUser(owner);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(messages.findBySessionIdOrderBySentAtAsc(session.getId())).thenReturn(List.of());
        when(ollama.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mô tả sản phẩm")))));
        var service = new AiChatServiceImpl(ollama, google, sessions, messages, users, posts,
                10 * 1024 * 1024L, 6, 20, 60, 30, "gemma4:31b-cloud");
        var response = service.finalizeChat(session.getId(), owner.getId());
        assertEquals("Mô tả sản phẩm", response.getDescription());
        assertNull(response.getSuggestedPrice(), "Only the paid valuation endpoint may produce a price");
        verify(ollama, times(1)).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        verifyNoInteractions(legacyCredits);
    }
}
