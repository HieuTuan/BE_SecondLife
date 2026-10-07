package com.secondlife.secondlife.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.RetryException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OllamaConfigTest {
    private ApplicationContextRunner configuration() {
        return new ApplicationContextRunner().withUserConfiguration(OllamaConfig.class)
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withBean(WebClient.Builder.class, WebClient::builder)
                .withPropertyValues("spring.ai.ollama.base-url=http://127.0.0.1:11434",
                        "spring.ai.ollama.chat.options.model=test-model", "ollama.api-key=",
                        "app.ai.connect-timeout-ms=1000", "app.ai.streaming-connect-timeout-ms=1000",
                        "app.ai.read-timeout-ms=50", "app.ai.max-retries=2",
                        "app.ai.retry-initial-interval-ms=1", "app.ai.retry-multiplier=1",
                        "app.ai.retry-max-interval-ms=1");
    }

    @Test
    void transientFailuresStopAtTheConfiguredRetryLimit() {
        configuration().run(context -> {
            assertThat(context).hasSingleBean(RetryTemplate.class);
            AtomicInteger attempts = new AtomicInteger();
            assertThrows(RetryException.class, () -> context.getBean(RetryTemplate.class).execute(() -> {
                attempts.incrementAndGet();
                throw new TransientAiException("temporary upstream failure");
            }));
            assertEquals(3, attempts.get());
        });
    }

    @Test
    void permanentFailuresAreNotRetried() {
        configuration().run(context -> {
            assertThat(context).hasSingleBean(RetryTemplate.class);
            AtomicInteger attempts = new AtomicInteger();
            assertThrows(RetryException.class, () -> context.getBean(RetryTemplate.class).execute(() -> {
                attempts.incrementAndGet();
                throw new NonTransientAiException("invalid upstream request");
            }));
            assertEquals(1, attempts.get());
        });
    }

    @Test
    void ollamaChatUsesTheConfiguredRetryPolicy() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer(503, "{\"error\":\"temporarily unavailable\"}", 0)) {
            configuration().withPropertyValues("spring.ai.ollama.base-url=" + server.baseUrl(),
                    "app.ai.read-timeout-ms=1000").run(context -> {
                assertThrows(RuntimeException.class, () -> context.getBean(OllamaChatModel.class).call("hello"));
                assertEquals(3, server.attempts.get());
            });
        }
    }

    @Test
    void configuredReadTimeoutStopsASlowOllamaResponse() throws Exception {
        try (LocalHttpServer server = new LocalHttpServer(200, "{\"models\":[]}", 400)) {
            configuration().withPropertyValues("spring.ai.ollama.base-url=" + server.baseUrl()).run(context ->
                    assertThrows(ResourceAccessException.class, () -> context.getBean(OllamaApi.class).listModels()));
        }
    }

    private static final class LocalHttpServer implements AutoCloseable {
        private final ServerSocket listener;
        private final Thread worker;
        private final AtomicInteger attempts = new AtomicInteger();

        private LocalHttpServer(int status, String body, long delayMillis) throws IOException {
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            worker = new Thread(() -> {
                while (!listener.isClosed()) {
                    try (Socket socket = listener.accept()) {
                        StringBuilder headers = new StringBuilder();
                        int next;
                        while ((next = socket.getInputStream().read()) != -1) {
                            headers.append((char) next);
                            if (headers.toString().endsWith("\r\n\r\n")) break;
                        }
                        int contentLength = 0;
                        for (String line : headers.toString().split("\r\n")) {
                            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                                contentLength = Integer.parseInt(line.substring(15).trim());
                            }
                        }
                        socket.getInputStream().readNBytes(contentLength);
                        attempts.incrementAndGet();
                        Thread.sleep(delayMillis);
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        String response = "HTTP/1.1 " + status + " Test\r\nContent-Type: application/json\r\nContent-Length: "
                                + bytes.length + "\r\nConnection: close\r\n\r\n";
                        socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                        socket.getOutputStream().write(bytes);
                        socket.getOutputStream().flush();
                    } catch (IOException ex) {
                        if (listener.isClosed()) return;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "ollama-config-test-server");
            worker.setDaemon(true);
            worker.start();
        }

        private String baseUrl() { return "http://127.0.0.1:" + listener.getLocalPort(); }

        @Override
        public void close() throws Exception {
            listener.close();
            worker.interrupt();
            worker.join(1000);
        }
    }
}
