package com.secondlife.secondlife.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderAutoReleaseProperties.class)
public class OrderReleaseConfiguration {
    @Bean("orderReleaseClock")
    Clock orderReleaseClock() { return Clock.systemUTC(); }
}
