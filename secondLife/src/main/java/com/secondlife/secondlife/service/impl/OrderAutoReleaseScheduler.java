package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.config.OrderAutoReleaseProperties;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.orders.auto-release.enabled", havingValue = "true", matchIfMissing = false)
public class OrderAutoReleaseScheduler {
    private final OrderRepository orders;
    private final OrderAutoReleaseService release;
    private final OrderAutoReleaseProperties properties;

    public OrderAutoReleaseScheduler(OrderRepository orders, OrderAutoReleaseService release, OrderAutoReleaseProperties properties) {
        this.orders = orders;
        this.release = release;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.orders.auto-release.interval-ms}")
    public void releaseDueOrders() {
        var cutoff = release.dueCutoff();
        java.time.Instant afterDelivery = null;
        java.util.UUID afterId = null;
        // Advance past failures too, so unresolved legacy orders cannot starve later batches.
        while (true) {
            var page = PageRequest.of(0, properties.batchSize());
            var batch = afterDelivery == null
                    ? orders.findDueSettlementOrders(OrderStatus.DELIVERED, EscrowStatus.HELD, cutoff, page)
                    : orders.findDueSettlementOrdersAfter(OrderStatus.DELIVERED, EscrowStatus.HELD, cutoff, afterDelivery, afterId, page);
            if (batch.isEmpty()) return;
            for (var due : batch) {
                try {
                    release.releaseIfDue(due.getId());
                } catch (RuntimeException ex) {
                    log.warn("Automatic settlement failed for order {} ({})", due.getId(), ex.getClass().getSimpleName());
                }
            }
            var last = batch.getLast(); afterDelivery = last.getDeliveredAt(); afterId = last.getId();
        }
    }
}
