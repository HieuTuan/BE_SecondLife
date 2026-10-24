package com.secondlife.secondlife.integration.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

public class SpringAiChatGateway implements AiChatGateway {
    private final ChatClient client;

    public SpringAiChatGateway(ChatModel model) {
        this(ChatClient.builder(model));
    }

    public SpringAiChatGateway(ChatClient.Builder builder) {
        this.client = builder.build();
    }

    @Override
    public String complete(Prompt prompt) {
        return client.prompt(prompt).call().content();
    }

    @Override
    public String complete(String prompt) {
        return client.prompt(prompt).call().content();
    }
}
