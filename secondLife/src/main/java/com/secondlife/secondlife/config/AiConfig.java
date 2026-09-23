package com.secondlife.secondlife.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    // Đánh dấu Google GenAI làm ChatModel chính để sửa lỗi xung đột 2 bean
    @Bean
    @Primary
    public ChatModel primaryChatModel(@Qualifier("googleGenAiChatModel") ChatModel googleChatModel) {
        return googleChatModel;
    }
}
