package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.entity.AiChatSession;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.AiChatMessageRepository;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.AiChatServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatConfigurationTest {
    private final ChatModel ollama = mock(ChatModel.class);
    private final ChatModel google = mock(ChatModel.class);
    private final AiChatSessionRepository sessions = mock(AiChatSessionRepository.class);
    private final AiChatMessageRepository messages = mock(AiChatMessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final User owner = owner();
    private final AiChatSession session = session(owner);

    @Test
    void configuredRateLimitRejectsChatAtTheLimit() {
        when(messages.countRecentUserMessages(eq(owner.getId()), any())).thenReturn(2L);
        var service = configuredService();

        assertThrows(ConflictException.class, () -> service.processChat(request(), owner.getId()));
    }

    @Test
    void configuredRateWindowControlsRecentMessageQuery() {
        var service = configuredService();
        Instant earliestCutoff = Instant.now().minusSeconds(120);

        service.processChat(request(), owner.getId());

        Instant latestCutoff = Instant.now().minusSeconds(120);
        var cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(messages).countRecentUserMessages(eq(owner.getId()), cutoff.capture());
        assertTrue(!cutoff.getValue().isBefore(earliestCutoff) && !cutoff.getValue().isAfter(latestCutoff));
    }

    @Test
    void configuredSessionLimitRejectsChatAtTheLimit() {
        session.setMessageCount(2);
        var service = configuredService();

        assertThrows(ConflictException.class, () -> service.processChat(request(), owner.getId()));
    }

    @Test
    void configuredImageSizeRejectsImageAboveListingLimit() {
        var request = request();
        request.setImages(List.of(new MockMultipartFile("images", "photo.jpg", "image/jpeg", new byte[4])));
        var service = configuredService();

        assertThrows(BadRequestException.class, () -> service.processChat(request, owner.getId()));
    }

    @Test
    void configuredImageCountRejectsImagesAboveListingLimit() {
        var request = request();
        request.setImages(List.of(
                new MockMultipartFile("images", "first.jpg", "image/jpeg", new byte[1]),
                new MockMultipartFile("images", "second.jpg", "image/jpeg", new byte[1]),
                new MockMultipartFile("images", "third.jpg", "image/jpeg", new byte[1])));
        var service = configuredService();

        assertThrows(BadRequestException.class, () -> service.processChat(request, owner.getId()));
    }

    @Test
    void imageChatSendsAllImagesInOneMessageToGoogle() {
        var request = request();
        request.setImages(List.of(
                new MockMultipartFile("images", "first.jpg", "image/jpeg", new byte[]{1}),
                new MockMultipartFile("images", "second.png", "image/png", new byte[]{2})));
        var service = configuredService();

        assertEquals("Product description", service.processChat(request, owner.getId()).getReply());

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(google).call(prompt.capture());
        verify(ollama, never()).call(any(Prompt.class));
        var userMessages = prompt.getValue().getInstructions().stream()
                .filter(UserMessage.class::isInstance).map(UserMessage.class::cast).toList();
        assertEquals(1, userMessages.size());
        assertEquals(request.getMessage(), userMessages.getFirst().getText());
        assertEquals(2, userMessages.getFirst().getMedia().size());
        assertEquals("image/jpeg", userMessages.getFirst().getMedia().getFirst().getMimeType().toString());
        assertEquals("image/png", userMessages.getFirst().getMedia().getLast().getMimeType().toString());
    }

    @ParameterizedTest
    @MethodSource("invalidChatSettings")
    void invalidChatSettingFailsStartup(String property, Object value) {
        var failure = assertThrows(BeanCreationException.class,
                () -> configuredService(Map.of(property, value)));

        assertInstanceOf(IllegalArgumentException.class, failure.getMostSpecificCause());
        assertTrue(failure.getMostSpecificCause().getMessage().contains(property));
    }

    private static Stream<Arguments> invalidChatSettings() {
        Stream<Arguments> numeric = Stream.of("app.listing.max-image-bytes", "app.listing.max-images",
                        "app.ai.chat.rate-limit-max-messages", "app.ai.chat.rate-limit-window-seconds",
                        "app.ai.chat.session-max-messages")
                .flatMap(property -> Stream.of(0, -1).map(value -> Arguments.of(property, value)));
        return Stream.concat(numeric, Stream.of(
                Arguments.of("spring.ai.ollama.chat.options.model", ""),
                Arguments.of("spring.ai.ollama.chat.options.model", "  ")));
    }

    @Test
    void textChatUsesConfiguredOllamaModel() {
        var service = configuredService();

        assertEquals("Product description", service.processChat(request(), owner.getId()).getReply());

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(ollama).call(prompt.capture());
        assertEquals("configured-chat-model", prompt.getValue().getOptions().getModel());
    }

    @Test
    void finalDescriptionUsesConfiguredOllamaModel() {
        var service = configuredService();

        assertEquals("Product description", service.finalizeChat(session.getId(), owner.getId()).getDescription());

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(ollama).call(prompt.capture());
        assertEquals("configured-chat-model", prompt.getValue().getOptions().getModel());
    }

    private AiChatServiceImpl configuredService() {
        return configuredService(Map.of());
    }

    private AiChatServiceImpl configuredService(Map<String, Object> overrides) {
        when(ollama.getOptions()).thenReturn(ChatOptions.builder().build());
        when(google.getOptions()).thenReturn(ChatOptions.builder().build());
        when(ollama.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Product description")))));
        when(google.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage("Product description")))));
        when(users.findByIdForRoleUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        try (var context = new AnnotationConfigApplicationContext()) {
            Map<String, Object> settings = new LinkedHashMap<>(Map.of(
                    "app.listing.max-image-bytes", 3L,
                    "app.listing.max-images", 2,
                    "app.ai.chat.rate-limit-max-messages", 2,
                    "app.ai.chat.rate-limit-window-seconds", 120L,
                    "app.ai.chat.session-max-messages", 2,
                    "spring.ai.ollama.chat.options.model", "configured-chat-model"));
            settings.putAll(overrides);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("chat-test", settings));
            context.registerBean("ollamaChatModel", ChatModel.class, () -> ollama);
            context.registerBean("googleGenAiChatModel", ChatModel.class, () -> google);
            context.registerBean(AiChatSessionRepository.class, () -> sessions);
            context.registerBean(AiChatMessageRepository.class, () -> messages);
            context.registerBean(UserRepository.class, () -> users);
            context.registerBean(PostRepository.class, () -> posts);
            context.registerBean(AiChatServiceImpl.class);
            context.refresh();
            return context.getBean(AiChatServiceImpl.class);
        }
    }

    private AiChatRequest request() {
        AiChatRequest request = new AiChatRequest();
        request.setSessionId(session.getId());
        request.setMessage("Describe the product");
        return request;
    }

    private static User owner() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static AiChatSession session(User user) {
        AiChatSession session = new AiChatSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setMessageCount(0);
        return session;
    }
}
