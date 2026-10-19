package com.secondlife.secondlife.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableCaching
public class CacheConfiguration {

    public static final String CACHE_CATEGORIES = "catalog:categories";
    public static final String CACHE_ITEMS = "catalog:items";

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(List.of(CACHE_CATEGORIES, CACHE_ITEMS));
        return cacheManager;
    }
}
