package com.secondlife.secondlife.security.userdetails;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class UserPrincipalCacheTest {
    private final AtomicLong time = new AtomicLong();
    private final UserPrincipalCache cache = new UserPrincipalCache(Duration.ofSeconds(1), 2, time::get);

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void capacityEvictsTheLeastRecentlyUsedPrincipal() {
        CustomUserDetails first = details(), second = details(), third = details();
        cache.put(first.getId(), first);
        cache.put(second.getId(), second);
        assertSame(first, cache.get(first.getId()));
        cache.put(third.getId(), third);
        assertNull(cache.get(second.getId()));
        assertSame(first, cache.get(first.getId()));
        assertSame(third, cache.get(third.getId()));
    }

    @Test
    void readsDoNotExtendTheExpirationDeadline() {
        CustomUserDetails details = details();
        cache.put(details.getId(), details);
        time.set(Duration.ofMillis(999).toNanos());
        assertSame(details, cache.get(details.getId()));
        time.set(Duration.ofSeconds(1).toNanos());
        assertNull(cache.get(details.getId()));
    }

    @Test
    void anInFlightLoadCannotRepopulateAnEvictedGeneration() {
        CustomUserDetails details = details();
        long generation = cache.generation();
        cache.evict(details.getId());
        cache.putIfUnchanged(details.getId(), details, generation);
        assertNull(cache.get(details.getId()));
    }

    @Test
    void anInFlightLoadCannotRepopulateAClearedGeneration() {
        CustomUserDetails details = details();
        long generation = cache.generation();
        cache.clear();
        cache.putIfUnchanged(details.getId(), details, generation);
        assertNull(cache.get(details.getId()));
    }

    @Test
    void evictionWaitsForSuccessfulCommit() {
        CustomUserDetails details = details();
        cache.put(details.getId(), details);
        beginTransaction();
        cache.evictAfterCommit(details.getId());
        assertSame(details, cache.get(details.getId()));
        TransactionSynchronizationUtils.triggerAfterCommit();
        assertNull(cache.get(details.getId()));
    }

    @Test
    void rollbackPreservesThePreviouslyCommittedPrincipal() {
        CustomUserDetails details = details();
        cache.put(details.getId(), details);
        beginTransaction();
        cache.clearAfterCommit();
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertSame(details, cache.get(details.getId()));
    }

    @Test
    void invalidationWithoutATransactionRunsImmediately() {
        CustomUserDetails details = details();
        cache.put(details.getId(), details);
        cache.evictAfterCommit(details.getId());
        assertNull(cache.get(details.getId()));
    }

    private void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private CustomUserDetails details() {
        User user = new User(UUID.randomUUID() + "@example.test", "hash", AccountStatus.ACTIVE);
        user.setId(UUID.randomUUID());
        return new CustomUserDetails(user);
    }
}
