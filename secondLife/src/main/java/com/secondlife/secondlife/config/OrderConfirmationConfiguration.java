package com.secondlife.secondlife.config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(OrderConfirmationProperties.class)
public class OrderConfirmationConfiguration {
    @Bean("orderConfirmationClock") Clock confirmationClock() { return Clock.systemUTC(); }
}
