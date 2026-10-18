package com.secondlife.secondlife.integration.ai;

import org.springframework.ai.chat.prompt.Prompt;

public interface AiChatGateway {
    String complete(Prompt prompt);
    String complete(String prompt);
}
