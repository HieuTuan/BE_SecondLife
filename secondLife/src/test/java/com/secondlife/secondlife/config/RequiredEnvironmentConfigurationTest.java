package com.secondlife.secondlife.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;
import java.util.ArrayList;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredEnvironmentConfigurationTest {
    private ApplicationContextRunner configuredRunner(String omittedVariable) throws IOException {
        Properties declared = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        ArrayList<String> fixtures = new ArrayList<>();
        for (String key : declared.stringPropertyNames()) {
            String expression = declared.getProperty(key);
            fixtures.add(key + "=" + expression);
            String variable = expression.substring(2, expression.length() - 1);
            if (!variable.equals(omittedVariable)) fixtures.add(variable + "=explicit-test-value");
        }
        return new ApplicationContextRunner().withUserConfiguration(StartupConfiguration.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
                .withPropertyValues(fixtures.toArray(String[]::new));
    }

    @Test
    void missingVariableForDisabledIntegrationStillFailsStartup() throws IOException {
        configuredRunner("GHN_TOKEN").withPropertyValues("GHN_ENABLED=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingRequiredVariableDoesNotExposeOtherSecrets() throws IOException {
        configuredRunner("SERVER_PORT").withPropertyValues("server.port=8080",
                        "DB_PASSWORD=private-test-password")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("server.port")
                            .hasMessageNotContaining("private-test-password");
                });
    }

    @Test
    void explicitlyConfiguredBlankCredentialCanBeUsedWhenIntegrationIsDisabled() throws IOException {
        configuredRunner(null).withPropertyValues("GHN_ENABLED=false", "GHN_TOKEN=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void blankGoogleClientIdFailsStartupRatherThanSkippingAudienceValidation() throws IOException {
        configuredRunner(null).withPropertyValues("GOOGLE_CLIENT_ID=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackages = "com.secondlife.secondlife.config", useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = ".*RequiredEnvironmentConfiguration"))
    static class StartupConfiguration {
    }
}
