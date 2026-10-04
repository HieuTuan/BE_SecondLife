package com.secondlife.secondlife.config;

import com.google.genai.Client;
import com.google.genai.HttpApiClient;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleGenAiConfigTest {
    private ApplicationContextRunner configuration() {
        return new ApplicationContextRunner().withUserConfiguration(GoogleGenAiConfig.class)
                .withPropertyValues("spring.ai.google.genai.api-key=dummy-google-api-key",
                        "app.ai.google.base-url=https://sdk-config.example", "app.ai.google.timeout-ms=4567",
                        "app.ai.google.connect-timeout-ms=1234", "app.ai.google.read-timeout-ms=2345",
                        "app.ai.google.write-timeout-ms=3456", "app.ai.google.retry-attempts=3",
                        "app.ai.google.retry-initial-delay-seconds=0", "app.ai.google.retry-max-delay-seconds=0",
                        "app.ai.google.retry-multiplier=1", "app.ai.google.retry-jitter=0",
                        "app.ai.google.retry-http-status-codes=418");
    }

    @Test
    void sdkUsesConfiguredEndpointAndTransportTimeouts() {
        configuration().run(context -> {
            Client client = context.getBean(Client.class);
            HttpApiClient api = api(client);
            Request request = ReflectionTestUtils.invokeMethod(api, "buildRequest", "GET", "models", "", Optional.empty());
            assertEquals("https://sdk-config.example/v1beta/models", request.url().toString());
            assertFalse(client.vertexAI());
            assertEquals(1234, api.httpClient().connectTimeoutMillis());
            assertEquals(2345, api.httpClient().readTimeoutMillis());
            assertEquals(3456, api.httpClient().writeTimeoutMillis());
            assertEquals(4567, api.httpClient().callTimeoutMillis());
        });
    }

    @Test
    void sdkRetriesOnlyConfiguredStatusesUpToConfiguredAttemptLimit() {
        configuration().run(context -> {
            HttpApiClient api = api(context.getBean(Client.class));
            Interceptor retry = api.httpClient().interceptors().getFirst();
            Request request = new Request.Builder().url("https://sdk-config.example/v1beta/models").build();
            Interceptor.Chain chain = mock(Interceptor.Chain.class);
            when(chain.request()).thenReturn(request);
            AtomicInteger attempts = new AtomicInteger();
            when(chain.proceed(any())).thenAnswer(invocation -> {
                attempts.incrementAndGet();
                return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(418).message("Configured retryable status")
                        .body(ResponseBody.create("{}", MediaType.get("application/json"))).build();
            });
            assertEquals(418, retry.intercept(chain).code());
            assertEquals(3, attempts.get());
        });
    }

    @Test
    void sdkDoesNotRetryAnUnconfiguredServerStatus() {
        configuration().run(context -> {
            Interceptor retry = api(context.getBean(Client.class)).httpClient().interceptors().getFirst();
            Request request = new Request.Builder().url("https://sdk-config.example/v1beta/models").build();
            Interceptor.Chain chain = mock(Interceptor.Chain.class);
            when(chain.request()).thenReturn(request);
            AtomicInteger attempts = new AtomicInteger();
            when(chain.proceed(any())).thenAnswer(invocation -> {
                attempts.incrementAndGet();
                return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                        .code(503).message("Unconfigured retry status")
                        .body(ResponseBody.create("{}", MediaType.get("application/json"))).build();
            });
            assertEquals(503, retry.intercept(chain).code());
            assertEquals(1, attempts.get());
        });
    }

    private HttpApiClient api(Client client) {
        return (HttpApiClient) ReflectionTestUtils.getField(client.models, "apiClient");
    }
}
