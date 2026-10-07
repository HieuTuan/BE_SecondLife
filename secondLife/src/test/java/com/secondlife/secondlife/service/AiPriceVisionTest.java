package com.secondlife.secondlife.service;

import com.secondlife.secondlife.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiPriceVisionTest {
    private static final String VALID = "{\"fairPriceMin\":1000000,\"fairPriceMax\":1500000,\"suggestedPrice\":1200000}";
    private final ChatModel text = mock(ChatModel.class);
    private final ChatModel vision = mock(ChatModel.class);
    private final AiPriceProvider provider = new OllamaAiPriceProvider(text, vision, JsonMapper.builder().build(),
            "gemma4:31b-cloud", "gemini-2.5-flash", "secondlife-test", 6);

    @Test void sendsEverySavedImageAsGeminiMediaAlongsideProductSnapshot() throws Exception {
        when(vision.call(any(Prompt.class))).thenReturn(response(VALID));
        var urls = List.of("https://res.cloudinary.com/secondlife-test/image/upload/v123/chair.jpg",
                "https://res.cloudinary.com/secondlife-test/image/upload/v123/back.png",
                "https://res.cloudinary.com/secondlife-test/image/upload/v123/side.webp");
        String snapshot = "{\"title\":\"Ghế gỗ\",\"description\":\"Có vết xước\"}";

        var result = provider.estimate(snapshot, urls);

        var captured = ArgumentCaptor.forClass(Prompt.class);
        verify(vision).call(captured.capture());
        verifyNoInteractions(text);
        Prompt prompt = captured.getValue();
        UserMessage message = (UserMessage) prompt.getInstructions().get(1);
        assertEquals(snapshot, message.getText());
        assertEquals(urls.size(), message.getMedia().size());
        assertEquals(List.of("image/jpeg", "image/png", "image/webp"),
                message.getMedia().stream().map(media -> media.getMimeType().toString()).toList());
        assertEquals(urls, message.getMedia().stream().map(media -> media.getData().toString()).toList());
        assertInstanceOf(GoogleGenAiChatOptions.class, prompt.getOptions());
        assertEquals("gemini-2.5-flash", prompt.getOptions().getModel());
        assertEquals("gemini-2.5-flash", result.modelVersion());

        // Verify the installed Google adapter emits actual image file data, rather than URL text.
        Method converter = GoogleGenAiChatModel.class.getDeclaredMethod("mediaToParts", Collection.class);
        converter.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<com.google.genai.types.Part> parts = (List<com.google.genai.types.Part>) converter.invoke(null, message.getMedia());
        for (int index = 0; index < urls.size(); index++) {
            assertEquals(urls.get(index), parts.get(index).fileData().orElseThrow().fileUri().orElseThrow());
            assertTrue(parts.get(index).text().isEmpty());
        }
    }

    @Test void rejectsUntrustedAndMalformedImageReferencesBeforeCallingEitherModel() {
        for (String url : List.of("http://res.cloudinary.com/other-cloud/image/upload/photo.jpg",
                "https://127.0.0.1/image.jpg", "https://res.cloudinary.com.evil.test/secondlife-test/image/upload/photo.jpg",
                "https://res.cloudinary.com/other-cloud/image/upload/photo.jpg",
                "https://res.cloudinary.com/secondlife-test/image/fetch/https://internal/photo.jpg",
                "https://res.cloudinary.com/secondlife-test/image/upload/../fetch/photo.jpg",
                "https://res.cloudinary.com/secondlife-test/image/upload/%2e%2e/fetch/photo.jpg",
                "https://user@res.cloudinary.com/secondlife-test/image/upload/photo.jpg",
                "https://res.cloudinary.com:443/secondlife-test/image/upload/photo.jpg",
                "https://res.cloudinary.com/secondlife-test/image/upload/photo.svg",
                "https://res.cloudinary.com/secondlife-test/image/upload/photo.jpg?redirect=https://internal")) {
            assertThrows(AiProviderException.class, () -> provider.estimate("product", List.of(url)), url);
        }
        verifyNoInteractions(text, vision);
    }

    @Test void upgradesLegacyCloudinaryHttpImagesToHttpsBeforeSendingToGemini() {
        when(vision.call(any(Prompt.class))).thenReturn(response(VALID));
        provider.estimate("product", List.of("http://res.cloudinary.com/secondlife-test/image/upload/v123/photo.jpg"));
        var captured = ArgumentCaptor.forClass(Prompt.class);
        verify(vision).call(captured.capture());
        var message = (UserMessage) captured.getValue().getInstructions().get(1);
        assertEquals("https://res.cloudinary.com/secondlife-test/image/upload/v123/photo.jpg",
                message.getMedia().getFirst().getData().toString());
    }

    @Test void invalidVisionPricesFailInsteadOfFallingBackToText() {
        for (String output : List.of("{\"error\":\"insufficient_product_information\"}", "not json",
                "{\"fairPriceMin\":100,\"fairPriceMax\":90,\"suggestedPrice\":95}",
                "{\"fairPriceMin\":10,\"fairPriceMax\":20,\"suggestedPrice\":15.001}")) {
            when(vision.call(any(Prompt.class))).thenReturn(response(output));
            assertThrows(AiProviderException.class, () -> provider.estimate("product", List.of(
                    "https://res.cloudinary.com/secondlife-test/image/upload/photo.jpg")));
        }
        verifyNoInteractions(text);
    }

    @Test void noSavedImagesPreservesLegacyTextValuation() {
        when(text.call(any(Prompt.class))).thenReturn(response(VALID));
        assertEquals("gemma4:31b-cloud", provider.estimate("product", List.of()).modelVersion());
        verifyNoInteractions(vision);
    }

    @Test void rejectsTooManyImagesAndEmptyVisionResponse() {
        String url = "https://res.cloudinary.com/secondlife-test/image/upload/photo.jpg";
        assertThrows(AiProviderException.class, () -> provider.estimate("product", java.util.Collections.nCopies(7, url)));
        verifyNoInteractions(text, vision);
        when(vision.call(any(Prompt.class))).thenReturn(null);
        assertThrows(AiProviderException.class, () -> provider.estimate("product", List.of(url)));
        verifyNoInteractions(text);
    }

    private ChatResponse response(String output) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(output))));
    }
}
