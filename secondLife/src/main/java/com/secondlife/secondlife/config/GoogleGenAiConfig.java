package com.secondlife.secondlife.config;

import com.google.genai.Client;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class GoogleGenAiConfig {
    @Bean(destroyMethod = "close")
    public Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey,
            @Value("${app.ai.google.base-url}") String baseUrl,
            @Value("${app.ai.google.timeout-ms}") int timeoutMs,
            @Value("${app.ai.google.connect-timeout-ms}") long connectTimeoutMs,
            @Value("${app.ai.google.read-timeout-ms}") long readTimeoutMs,
            @Value("${app.ai.google.write-timeout-ms}") long writeTimeoutMs,
            @Value("${app.ai.google.retry-attempts}") int retryAttempts,
            @Value("${app.ai.google.retry-initial-delay-seconds}") double initialDelaySeconds,
            @Value("${app.ai.google.retry-max-delay-seconds}") double maxDelaySeconds,
            @Value("${app.ai.google.retry-multiplier}") double retryMultiplier,
            @Value("${app.ai.google.retry-jitter}") double retryJitter,
            @Value("${app.ai.google.retry-http-status-codes}") List<Integer> retryHttpStatusCodes) {
        HttpRetryOptions retryOptions = HttpRetryOptions.builder()
                .attempts(retryAttempts)
                .initialDelay(initialDelaySeconds)
                .maxDelay(maxDelaySeconds)
                .expBase(retryMultiplier)
                .jitter(retryJitter)
                .httpStatusCodes(retryHttpStatusCodes)
                .build();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .writeTimeout(Duration.ofMillis(writeTimeoutMs))
                .callTimeout(Duration.ofMillis(timeoutMs))
                .build();
        return Client.builder().apiKey(apiKey).vertexAI(false)
                .httpOptions(HttpOptions.builder().baseUrl(baseUrl).timeout(timeoutMs).retryOptions(retryOptions).build())
                .clientOptions(ClientOptions.builder().customHttpClient(httpClient).build())
                .build();
    }
}
