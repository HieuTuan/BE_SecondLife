package com.secondlife.secondlife.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
@EnableConfigurationProperties(VnptEkycProperties.class)
public class VnptEkycConfig {
    @Bean("vnptRestTemplate")
    public RestTemplate vnptRestTemplate(VnptEkycProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        return new RestTemplate(factory);
    }
}
