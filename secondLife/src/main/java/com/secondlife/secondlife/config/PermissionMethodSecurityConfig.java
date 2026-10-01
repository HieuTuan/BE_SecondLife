package com.secondlife.secondlife.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@ConditionalOnProperty(name = "app.security.permissions-enabled", havingValue = "true", matchIfMissing = true)
public class PermissionMethodSecurityConfig {
}
