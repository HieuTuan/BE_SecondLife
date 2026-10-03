package com.secondlife.secondlife.service;

import com.secondlife.secondlife.exception.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OllamaAiPriceProviderTest {
    private AiPriceProvider provider(String output) {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(output)))));
        return new OllamaAiPriceProvider(model, JsonMapper.builder().build(), "gemma4:31b-cloud");
    }
    @Test void acceptsJsonWithValidVndRange() {
        var result = provider("{\"fairPriceMin\":1000000,\"fairPriceMax\":1500000,\"suggestedPrice\":1200000,\"expectedSellTime\":\"1-2 tuần\"}").estimate("product");
        assertEquals(new BigDecimal("1200000.00"), result.suggested());
        assertEquals("gemma4:31b-cloud", result.modelVersion());
    }
    @Test void rejectsMalformedOutputInsteadOfInventingZero() {
        assertThrows(AiProviderException.class, () -> provider("Giá khoảng 1.000.000").estimate("product"));
    }
    @Test void rejectsNegativeOrInconsistentRange() {
        for (var output : List.of(
                "{\"fairPriceMin\":0,\"fairPriceMax\":20,\"suggestedPrice\":10}",
                "{\"fairPriceMin\":30,\"fairPriceMax\":20,\"suggestedPrice\":25}",
                "{\"fairPriceMin\":10,\"fairPriceMax\":20,\"suggestedPrice\":25}",
                "{\"fairPriceMin\":\"10\",\"fairPriceMax\":20,\"suggestedPrice\":15}"))
            assertThrows(AiProviderException.class, () -> provider(output).estimate("product"));
    }
}
