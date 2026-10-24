package com.secondlife.secondlife.security.userdetails;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserPrincipalCache {

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofSeconds(60);

    private record CacheEntry(CustomUserDetails details, long expiryNanos) {
        boolean isExpired() {
            return System.nanoTime() >= expiryNanos;
        }
    }

    public CustomUserDetails get(UUID userId) {
        if (userId == null) {
            return null;
        }
        CacheEntry entry = cache.get(userId);
        if (entry == null || entry.isExpired()) {
            cache.remove(userId);
            return null;
        }
        return entry.details();
    }

    public void put(UUID userId, CustomUserDetails details) {
        if (userId != null && details != null) {
            cache.put(userId, new CacheEntry(details, System.nanoTime() + ttl.toNanos()));
        }
    }

    public void evict(UUID userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    public void clear() {
        cache.clear();
    }
}
