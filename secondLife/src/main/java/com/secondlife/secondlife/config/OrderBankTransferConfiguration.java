package com.secondlife.secondlife.config;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.time.Clock;
@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(OrderBankTransferProperties.class)
public class OrderBankTransferConfiguration {
    @Bean("bankPaymentClock") Clock bankPaymentClock() { return Clock.systemUTC(); }
}
