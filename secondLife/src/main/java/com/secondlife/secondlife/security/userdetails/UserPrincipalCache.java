package com.secondlife.secondlife.security.userdetails;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

@Component
public class UserPrincipalCache {

    private final Map<UUID, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final long ttlNanos;
    private final int maximumSize;
    private final LongSupplier nanoTime;
    private long generation;

    public UserPrincipalCache() {
        this(Duration.ofSeconds(60), 10_000, System::nanoTime);
    }

    @Autowired
    public UserPrincipalCache(@Value("${app.security.principal-cache.ttl-ms:60000}") long ttlMillis,
                              @Value("${app.security.principal-cache.maximum-size:10000}") int maximumSize) {
        this(Duration.ofMillis(ttlMillis), maximumSize, System::nanoTime);
    }

    UserPrincipalCache(Duration ttl, int maximumSize, LongSupplier nanoTime) {
        if (ttl.isZero() || ttl.isNegative() || maximumSize < 1) {
            throw new IllegalArgumentException("Principal cache TTL and maximum size must be positive");
        }
        this.ttlNanos = ttl.toNanos();
        this.maximumSize = maximumSize;
        this.nanoTime = nanoTime;
    }

    private record CacheEntry(CustomUserDetails details, long expiryNanos) {
        boolean isExpired(long now) {
            return now - expiryNanos >= 0;
        }
    }

    public synchronized CustomUserDetails get(UUID userId) {
        if (userId == null) {
            return null;
        }
        CacheEntry entry = cache.get(userId);
        if (entry == null || entry.isExpired(nanoTime.getAsLong())) {
            cache.remove(userId);
            return null;
        }
        return entry.details();
    }

    public synchronized void put(UUID userId, CustomUserDetails details) {
        putIfUnchanged(userId, details, generation);
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized void putIfUnchanged(UUID userId, CustomUserDetails details, long expectedGeneration) {
        if (generation != expectedGeneration) return;
        if (userId != null && details != null) {
            cache.put(userId, new CacheEntry(details, nanoTime.getAsLong() + ttlNanos));
            if (cache.size() > maximumSize) {
                removeExpired();
                while (cache.size() > maximumSize) {
                    var oldest = cache.keySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
            }
        }
    }

    public synchronized void evict(UUID userId) {
        if (userId != null) {
            cache.remove(userId);
            generation++;
        }
    }

    public synchronized void clear() {
        cache.clear();
        generation++;
    }

    @Scheduled(fixedDelayString = "${app.security.principal-cache.cleanup-interval-ms:60000}")
    public synchronized void removeExpired() {
        long now = nanoTime.getAsLong();
        cache.values().removeIf(entry -> entry.isExpired(now));
    }

    public void evictAfterCommit(UUID userId) {
        afterCommit(() -> evict(userId));
    }

    public void clearAfterCommit() {
        afterCommit(this::clear);
    }

    private void afterCommit(Runnable mutation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            mutation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mutation.run();
            }
        });
    }
}
