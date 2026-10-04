package com.secondlife.secondlife.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class SellerOnboardingPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Binding.class);

    @Test void missingSettingsFailStartup() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test void explicitSettingsBind() {
        configured().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SellerOnboardingProperties.class).getOtpTtlSeconds()).isEqualTo(600);
        });
    }

    @Test void zeroAttemptsFailStartup() {
        configured().withPropertyValues("app.seller-onboarding.email.max-attempts=0")
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner configured() {
        return runner.withPropertyValues("app.seller-onboarding.email.otp-ttl-seconds=600",
                "app.seller-onboarding.email.resend-cooldown-seconds=60",
                "app.seller-onboarding.email.max-attempts=5");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SellerOnboardingProperties.class)
    static class Binding {}
}
