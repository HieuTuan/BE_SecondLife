package com.secondlife.secondlife.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class LalamovePropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("app.shipping.lalamove.enabled=false", "app.shipping.lalamove.base-url=https://rest.sandbox.lalamove.com",
                    "app.shipping.lalamove.key=", "app.shipping.lalamove.secret=", "app.shipping.lalamove.market=VN",
                    "app.shipping.lalamove.language=vi_VN", "app.shipping.lalamove.service-type=MOTORCYCLE",
                    "app.shipping.lalamove.timeout-ms=5000", "app.shipping.lalamove.max-response-characters=8192",
                    "app.shipping.lalamove.webhook-max-future-skew-seconds=300");

    @Test void explicitlyDisabledConfigurationBindsWithoutCredentials() {
        runner.run(context -> { assertThat(context).hasNotFailed(); assertThat(context.getBean(LalamoveProperties.class).getMarket()).isEqualTo("VN"); });
    }
    @Test void enabledConfigurationNeedsMatchingCredentialEnvironment() {
        runner.withPropertyValues("app.shipping.lalamove.enabled=true").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.shipping.lalamove.enabled=true", "app.shipping.lalamove.key=pk_prod_key", "app.shipping.lalamove.secret=sk_prod_secret")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void invalidTimeoutAndResponseLimitFailBinding() {
        runner.withPropertyValues("app.shipping.lalamove.timeout-ms=0").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.shipping.lalamove.max-response-characters=0").run(context -> assertThat(context).hasFailed());
    }
    @Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(LalamoveProperties.class)
    static class TestConfiguration { }
}
