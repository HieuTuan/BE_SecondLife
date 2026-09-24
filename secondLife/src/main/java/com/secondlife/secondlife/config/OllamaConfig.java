package com.secondlife.secondlife.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url:http://127.0.0.1:11434}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.options.model:gemma4:31b-cloud}")
    private String defaultModel;

    @Value("${ollama.api-key:#{null}}")
    private String apiKey;

    @Bean
    public OllamaApi ollamaApi(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder) {
        if (apiKey != null && !apiKey.isEmpty() && !apiKey.equals("demo")) {
            // Apply API key for cloud Ollama
            restClientBuilder.defaultHeader("Authorization", "Bearer " + apiKey);
            webClientBuilder.defaultHeader("Authorization", "Bearer " + apiKey);
        }

        return OllamaApi.builder()
                .baseUrl(baseUrl)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();
    }

    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder().model(defaultModel).build())
                .build();
    }
}
