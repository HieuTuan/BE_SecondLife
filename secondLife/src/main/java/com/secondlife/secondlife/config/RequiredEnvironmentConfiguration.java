package com.secondlife.secondlife.config;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class RequiredEnvironmentConfiguration {
    private static final Set<String> NON_BLANK_SETTINGS = Set.of("spring.application.name", "app.jwt.secret",
            "app.google.client-id", "app.cloudinary.cloud-name", "app.cloudinary.api-key", "app.cloudinary.api-secret",
            "spring.ai.google.genai.api-key", "spring.ai.google.genai.chat.options.model",
            "spring.ai.ollama.base-url", "spring.ai.ollama.chat.options.model", "app.ai.google.base-url",
            "app.media.default-folder");

    @Bean
    static BeanFactoryPostProcessor requiredEnvironmentVariables(Environment environment) throws IOException {
        Properties declared = PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));
        return beanFactory -> {
            // Resolve every declared setting, including settings of disabled providers, before creating services.
            for (String key : declared.stringPropertyNames().stream().sorted().toList()) {
                try {
                    String value = environment.resolveRequiredPlaceholders(declared.getProperty(key));
                    if (NON_BLANK_SETTINGS.contains(key) && value.isBlank()) {
                        throw new IllegalArgumentException("Blank required configuration");
                    }
                } catch (IllegalArgumentException error) {
                    // Placeholder errors can contain values: report only the configuration key.
                    throw new BeanDefinitionStoreException("Required configuration missing or unresolved: " + key);
                }
            }
        };
    }
}
