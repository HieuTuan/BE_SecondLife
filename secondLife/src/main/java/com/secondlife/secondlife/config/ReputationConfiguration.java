package com.secondlife.secondlife.config;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReputationProperties.class)
public class ReputationConfiguration { }
