package com.secondlife.secondlife.config;

import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.integration.ai.SpringAiChatGateway;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiChatGatewayConfig {
    @Bean("defaultChatGateway")
    public AiChatGateway defaultChatGateway(ChatClient.Builder builder) {
        return new SpringAiChatGateway(builder);
    }

    @Bean("ollamaChatGateway")
    public AiChatGateway ollamaChatGateway(@Qualifier("ollamaChatModel") ChatModel model) {
        return new SpringAiChatGateway(model);
    }

    @Bean("googleChatGateway")
    @Primary
    public AiChatGateway googleChatGateway(@Qualifier("googleGenAiChatModel") ChatModel model) {
        return new SpringAiChatGateway(model);
    }
}
