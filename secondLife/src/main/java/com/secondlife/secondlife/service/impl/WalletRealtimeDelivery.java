package com.secondlife.secondlife.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Component
public class WalletRealtimeDelivery {
    private static final Logger log = LoggerFactory.getLogger(WalletRealtimeDelivery.class);
    private final SimpMessagingTemplate transport;

    public WalletRealtimeDelivery(SimpMessagingTemplate transport) {
        this.transport = transport;
    }

    public record Changed(UUID ownerId, UUID walletId) {}
    public record Invalidation(UUID eventId, int schemaVersion, String eventType,
                               String resourceType, UUID resourceId, Instant occurredAt) {}

    @TransactionalEventListener
    public void resourceCommitted(com.secondlife.secondlife.entity.ResourceChanged change) {
        var event = new Invalidation(UUID.randomUUID(), 1, change.type() + "_CHANGED",
                change.type(), change.id(), Instant.now());
        for (UUID owner : change.owners()) {
            try {
                transport.convertAndSendToUser(owner.toString(), "/queue/updates", event);
            } catch (RuntimeException e) {
                log.warn("Resource update delivery failed for event {}", event.eventId());
            }
        }
    }

    @TransactionalEventListener
    public void afterCommit(Changed change) {
        var event = new Invalidation(UUID.randomUUID(), 1, "WALLET_CHANGED",
                "WALLET", change.walletId(), Instant.now());
        try {
            transport.convertAndSendToUser(change.ownerId().toString(), "/queue/wallet-updates", event);
        } catch (RuntimeException e) {
            log.warn("Wallet update delivery failed for event {}", event.eventId());
        }
    }
}
