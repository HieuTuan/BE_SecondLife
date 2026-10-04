package com.secondlife.secondlife.config;

import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.options.model}")
    private String defaultModel;

    @Value("${ollama.api-key}")
    private String apiKey;

    @Bean
    public OllamaApi ollamaApi(RestClient.Builder restClientBuilder, WebClient.Builder webClientBuilder,
            @Value("${app.ai.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${app.ai.streaming-connect-timeout-ms}") int streamingConnectTimeoutMs,
            @Value("${app.ai.read-timeout-ms}") long readTimeoutMs) {
        restClientBuilder.requestFactory(ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(builder -> builder.disableAutomaticRetries()).build(
                HttpClientSettings.defaults().withTimeouts(Duration.ofMillis(connectTimeoutMs),
                        Duration.ofMillis(readTimeoutMs))));
        HttpClient httpClient = HttpClient.create().option(ChannelOption.CONNECT_TIMEOUT_MILLIS, streamingConnectTimeoutMs)
                .responseTimeout(readTimeoutMs == 0 ? null : Duration.ofMillis(readTimeoutMs));
        webClientBuilder.clientConnector(new ReactorClientHttpConnector(httpClient));
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
    public RetryTemplate aiRetryTemplate(@Value("${app.ai.max-retries}") long maxRetries,
            @Value("${app.ai.retry-initial-interval-ms}") long initialIntervalMs,
            @Value("${app.ai.retry-multiplier}") double multiplier,
            @Value("${app.ai.retry-max-interval-ms}") long maxIntervalMs) {
        return new RetryTemplate(RetryPolicy.builder()
                .maxRetries(maxRetries)
                .includes(TransientAiException.class, ResourceAccessException.class)
                .delay(Duration.ofMillis(initialIntervalMs))
                .multiplier(multiplier)
                .maxDelay(Duration.ofMillis(maxIntervalMs))
                .build());
    }

    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi, RetryTemplate aiRetryTemplate) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .retryTemplate(aiRetryTemplate)
                .options(OllamaChatOptions.builder().model(defaultModel).build())
                .build();
    }
}
