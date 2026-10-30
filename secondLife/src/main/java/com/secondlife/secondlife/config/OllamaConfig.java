package com.secondlife.secondlife.config;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
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
import reactor.netty.http.client.HttpClient;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;

@Slf4j
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
        boolean hasApiKey = hasValidApiKey(apiKey);
        if (hasApiKey) {
            // Apply API key for Ollama Cloud Direct API (https://ollama.com)
            restClientBuilder.defaultHeader("Authorization", "Bearer " + apiKey.trim());
            webClientBuilder.defaultHeader("Authorization", "Bearer " + apiKey.trim());
            if (isLoopbackUrl(baseUrl)) {
                log.warn("OLLAMA_API_KEY is configured, but SPRING_AI_OLLAMA_BASE_URL='{}' points to localhost instead of https://ollama.com.",
                        baseUrl);
            }
        } else if (isOllamaCloudUrl(baseUrl)) {
            log.warn("Ollama Cloud URL ('{}') is configured without OLLAMA_API_KEY; requests will fall back directly to Google Gemini until OLLAMA_API_KEY is provided.",
                    baseUrl);
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
                .predicate(throwable -> !hasUnreachableHostCause(throwable))
                .delay(Duration.ofMillis(initialIntervalMs))
                .multiplier(multiplier)
                .maxDelay(Duration.ofMillis(maxIntervalMs))
                .build());
    }

    private static boolean hasUnreachableHostCause(Throwable throwable) {
        Throwable cursor = throwable;
        int depth = 0;
        while (cursor != null && depth++ < 16) {
            if (cursor instanceof ConnectException || cursor instanceof UnknownHostException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    @Bean
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi, RetryTemplate aiRetryTemplate) {
        String resolvedModel = normalizeCloudModelName(baseUrl, defaultModel);
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .retryTemplate(aiRetryTemplate)
                .options(OllamaChatOptions.builder().model(resolvedModel).build())
                .build();
    }

    public static String normalizeCloudModelName(String baseUrl, String model) {
        if (model == null) {
            return null;
        }
        String trimmed = model.trim();
        if (!isOllamaCloudUrl(baseUrl)) {
            return trimmed;
        }
        if (trimmed.endsWith("-cloud")) {
            return trimmed.substring(0, trimmed.length() - "-cloud".length());
        }
        if (trimmed.endsWith(":cloud")) {
            return trimmed.substring(0, trimmed.length() - ":cloud".length());
        }
        return trimmed;
    }

    public static boolean isCloudUrlWithoutApiKey(String baseUrl, String apiKey) {
        return isOllamaCloudUrl(baseUrl) && !hasValidApiKey(apiKey);
    }

    public static boolean isOllamaCloudUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        String normalized = baseUrl.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("ollama.com");
    }

    private static boolean isLoopbackUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        String normalized = baseUrl.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("127.0.0.1") || normalized.contains("localhost");
    }

    private static boolean hasValidApiKey(String apiKey) {
        return apiKey != null && !apiKey.trim().isEmpty() && !"demo".equalsIgnoreCase(apiKey.trim());
    }
}
