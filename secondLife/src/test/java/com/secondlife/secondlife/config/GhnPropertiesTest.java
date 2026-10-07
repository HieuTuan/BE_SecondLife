package com.secondlife.secondlife.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GhnPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class)
            .withPropertyValues("app.shipping.ghn.enabled=false",
                    "app.shipping.ghn.base-url=https://dev-online-gateway.ghn.vn/shiip/public-api",
                    "app.shipping.ghn.token=", "app.shipping.ghn.shop-id=0", "app.shipping.ghn.webhook-secret=",
                    "app.shipping.ghn.timeout-ms=7000", "app.shipping.ghn.quote-ttl-seconds=120",
                    "app.shipping.ghn.max-response-characters=8192",
                    "app.shipping.ghn.webhook-max-future-skew-seconds=30");

    @Test
    void explicitDisabledConfigurationBindsWithoutCredentials() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(GhnProperties.class).getTimeoutMs()).isEqualTo(7000);
            assertThat(context.getBean(GhnProperties.class).getQuoteTtlSeconds()).isEqualTo(120);
        });
    }

    @Test
    void enabledProviderWithoutCredentialsFailsStartup() {
        runner.withPropertyValues("app.shipping.ghn.enabled=true").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidTimeoutFailsStartupInsteadOfFallingBackToAClampedValue() {
        runner.withPropertyValues("app.shipping.ghn.timeout-ms=0").run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GhnProperties.class)
    static class BindingConfiguration {
    }
}
