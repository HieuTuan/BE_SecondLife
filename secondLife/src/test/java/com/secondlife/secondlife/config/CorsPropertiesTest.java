package com.secondlife.secondlife.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void missingCorsOriginsFailsStartup() {
        contextRunner.run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    void configuredCorsOriginsBindSuccessfully() {
        contextRunner.withPropertyValues("app.cors.allowed-origins=https://example.test",
                        "app.cors.allowed-methods=GET,POST", "app.cors.allowed-headers=Authorization,Content-Type",
                        "app.cors.allow-credentials=true", "app.cors.max-age-seconds=60")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                            .isEqualTo("https://example.test");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    static class TestConfiguration {
    }
}
