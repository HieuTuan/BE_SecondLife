package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.AiChatMessage;
import com.secondlife.secondlife.entity.AiChatSession;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.AiChatMessageRepository;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MainFlowDescriptionTest {
    @Test
    void finalizingDescriptionDoesNotPerformUnchargedValuation() {
        ChatModel ollama = mock(ChatModel.class);
        ChatModel google = mock(ChatModel.class);
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        AiChatSessionRepository sessions = mock(AiChatSessionRepository.class);
        AiChatMessageRepository messages = mock(AiChatMessageRepository.class);
        UserRepository users = mock(UserRepository.class);
        PostRepository posts = mock(PostRepository.class);
        CreditService legacyCredits = mock(CreditService.class);
        User owner = new User(); owner.setId(UUID.randomUUID());
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        AiChatSession session = new AiChatSession();
        session.setId(UUID.randomUUID()); session.setUser(owner);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(messages.findBySessionIdOrderBySentAtAsc(session.getId())).thenReturn(List.of());
        when(ollama.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Mô tả sản phẩm")))));
        var service =
                new AiChatServiceImpl(ollama, google, sessions, messages, users, posts,
                10 * 1024 * 1024L, 6, 20, 60, 30, "gemma4:31b-cloud", mock(ListingCreditService.class));
        var response = service.finalizeChat(session.getId(), owner.getId());
        assertEquals("Mô tả sản phẩm", response.description());
        assertNull(response.suggestedPrice(), "Only the paid valuation endpoint may produce a price");
        verify(ollama, times(1)).call(any(Prompt.class));
        verifyNoInteractions(legacyCredits);
    }

    @Test
    void finalizingDescriptionFallsBackToGoogleWhenOllamaFails() {
        ChatModel ollama = mock(ChatModel.class);
        ChatModel google = mock(ChatModel.class);
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        AiChatSessionRepository sessions = mock(AiChatSessionRepository.class);
        AiChatMessageRepository messages = mock(AiChatMessageRepository.class);
        UserRepository users = mock(UserRepository.class);
        PostRepository posts = mock(PostRepository.class);
        User owner = new User(); owner.setId(UUID.randomUUID());
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        AiChatSession session = new AiChatSession();
        session.setId(UUID.randomUUID()); session.setUser(owner);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(messages.findBySessionIdOrderBySentAtAsc(session.getId())).thenReturn(List.of());
        when(ollama.call(any(Prompt.class))).thenThrow(new ResourceAccessException("Connection refused"));
        when(google.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("<title>Test Title</title><desc>Fallback Description</desc>")))));
        var service =
                new AiChatServiceImpl(ollama, google, sessions, messages, users, posts,
                10 * 1024 * 1024L, 6, 20, 60, 30, "gemma4:31b-cloud", mock(ListingCreditService.class));
        var response = service.finalizeChat(session.getId(), owner.getId());
        assertEquals("Fallback Description", response.description());
        assertEquals("Test Title", response.title());
        verify(ollama, times(1)).call(any(Prompt.class));
        verify(google, times(1)).call(any(Prompt.class));
    }

    @Test
    void subsequentFinalizeSkipsDeadOllamaDuringCooldownWindow() {
        ChatModel ollama = mock(ChatModel.class);
        ChatModel google = mock(ChatModel.class);
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        AiChatSessionRepository sessions = mock(AiChatSessionRepository.class);
        AiChatMessageRepository messages = mock(AiChatMessageRepository.class);
        UserRepository users = mock(UserRepository.class);
        PostRepository posts = mock(PostRepository.class);
        User owner = new User(); owner.setId(UUID.randomUUID());
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));

        AiChatSession firstSession = new AiChatSession();
        firstSession.setId(UUID.randomUUID());
        firstSession.setUser(owner);
        AiChatSession secondSession = new AiChatSession();
        secondSession.setId(UUID.randomUUID());
        secondSession.setUser(owner);

        when(sessions.findById(firstSession.getId())).thenReturn(Optional.of(firstSession));
        when(sessions.findById(secondSession.getId())).thenReturn(Optional.of(secondSession));
        when(messages.findBySessionIdOrderBySentAtAsc(any())).thenReturn(List.of());
        when(ollama.call(any(Prompt.class))).thenThrow(new ResourceAccessException("Connection refused"));
        when(google.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("<title>Tiêu đề</title><desc>Mô tả nhanh từ Gemini</desc>")))));

        var service = new AiChatServiceImpl(ollama, google, sessions, messages, users, posts,
                10 * 1024 * 1024L, 6, 20, 60, 30, "gemma4:31b-cloud", mock(ListingCreditService.class));

        service.finalizeChat(firstSession.getId(), owner.getId());
        service.finalizeChat(secondSession.getId(), owner.getId());

        verify(ollama, times(1)).call(any(Prompt.class));
        verify(google, times(2)).call(any(Prompt.class));
    }

    @Test
    void finalizingDescriptionStripsVisionAuditInstructionsFromHistory() {
        ChatModel ollama = mock(ChatModel.class);
        ChatModel google = mock(ChatModel.class);
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        AiChatSessionRepository sessions = mock(AiChatSessionRepository.class);
        AiChatMessageRepository messages = mock(AiChatMessageRepository.class);
        UserRepository users = mock(UserRepository.class);
        PostRepository posts = mock(PostRepository.class);
        User owner = new User(); owner.setId(UUID.randomUUID());
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        AiChatSession session = new AiChatSession();
        session.setId(UUID.randomUUID()); session.setUser(owner);
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

        AiChatMessage initMsg = new AiChatMessage();
        initMsg.setRole("USER");
        initMsg.setMessageContent("""
                Người bán đang đăng bài với thông tin khai báo ở Bước 1:
                - Danh mục sản phẩm đã chọn: "Gia dụng"
                - Loại thiết bị/sản phẩm đã chọn: "Giàn phơi"

                NHIỆM VỤ BẮT BUỘC (KIỂM TRA HÌNH ẢNH SẢN PHẨM):
                [CẢNH_BÁO_KHÔNG_KHỚP_LOAI] rất dài...
                """);
        AiChatMessage initReply = new AiChatMessage();
        initReply.setRole("ASSISTANT");
        initReply.setMessageContent("[HỢP_LỆ] Giàn phơi inox sáng bóng không rỉ sét.");

        when(messages.findBySessionIdOrderBySentAtAsc(session.getId())).thenReturn(List.of(initMsg, initReply));
        when(ollama.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("<title>Giàn Phơi Inox</title><desc>Mô tả chi tiết</desc>")))));

        var service = new AiChatServiceImpl(ollama, google, sessions, messages, users, posts,
                10 * 1024 * 1024L, 6, 20, 60, 30, "gemma4:31b-cloud", mock(ListingCreditService.class));
        service.finalizeChat(session.getId(), owner.getId());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(ollama).call(promptCaptor.capture());
        String combinedUserPrompts = promptCaptor.getValue().getInstructions().stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).getText())
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(combinedUserPrompts.contains("Loại thiết bị/sản phẩm đã chọn: \"Giàn phơi\""));
        assertFalse(combinedUserPrompts.contains("NHIỆM VỤ BẮT BUỘC (KIỂM TRA HÌNH ẢNH SẢN PHẨM)"));
        assertTrue(combinedUserPrompts.contains("KHÔNG dùng bất kỳ emoji"));
    }

    @Test
    void stripEmojisRemovesIconsWhilePreservingMarkdownBoldAndListFormatting() {
        String rawWithEmojis = """
                **🌟 Tình trạng sản phẩm:**
                * **Độ mới & Ngoại quan:** Đẹp 99% như mới.
                ⚙️ **Thông số kỹ thuật:**
                * 💡 Công suất: 2000W
                """;
        String cleaned = AiChatServiceImpl.stripEmojis(rawWithEmojis);
        assertFalse(cleaned.contains("🌟"));
        assertFalse(cleaned.contains("⚙️"));
        assertFalse(cleaned.contains("💡"));
        assertTrue(cleaned.contains("**Tình trạng sản phẩm:**"));
        assertTrue(cleaned.contains("* **Độ mới & Ngoại quan:** Đẹp 99% như mới."));
        assertTrue(cleaned.contains("**Thông số kỹ thuật:**"));
        assertTrue(cleaned.contains("* Công suất: 2000W"));
    }
}
