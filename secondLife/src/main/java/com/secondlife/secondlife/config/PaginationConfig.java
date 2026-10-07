package com.secondlife.secondlife.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;

@Configuration(proxyBeanMethods = false)
public class PaginationConfig {
    @Bean
    PageableHandlerMethodArgumentResolverCustomizer paginationDefaults(
            @Value("${app.pagination.default-page}") int defaultPage,
            @Value("${app.pagination.default-size}") int defaultSize,
            @Value("${app.pagination.max-size}") int maxSize) {
        if (defaultPage < 0 || defaultSize <= 0 || maxSize < defaultSize) {
            throw new IllegalArgumentException("Pagination requires page >= 0 and 0 < default size <= maximum size");
        }
        return resolver -> {
            resolver.setFallbackPageable(PageRequest.of(defaultPage, defaultSize));
            resolver.setMaxPageSize(maxSize);
        };
    }
}
