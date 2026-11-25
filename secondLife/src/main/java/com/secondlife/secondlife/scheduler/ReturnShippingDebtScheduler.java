package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.repository.ReturnShippingFeeRepository;
import com.secondlife.secondlife.service.ReturnShippingFeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.orders.return-shipping-debt.enabled", havingValue = "true", matchIfMissing = true)
public class ReturnShippingDebtScheduler {
    private static final int BATCH_SIZE = 50;

    private final ReturnShippingFeeRepository fees;
    private final ReturnShippingFeeService service;
    private UUID afterId;

    @Scheduled(fixedDelayString = "${app.orders.return-shipping-debt.interval-ms:10000}",
            initialDelayString = "${app.orders.return-shipping-debt.interval-ms:10000}")
    public void collectOutstandingDebt() {
        var collectible = fees.findCollectibleDebtIds(afterId, PageRequest.of(0, BATCH_SIZE));
        afterId = collectible.size() < BATCH_SIZE ? null : collectible.getLast();
        for (UUID shipmentId : collectible) {
            try {
                service.collectOutstanding(shipmentId);
            } catch (RuntimeException failure) {
                log.warn("Automatic return shipping debt collection failed for shipment {} ({})",
                        shipmentId, failure.getClass().getSimpleName());
            }
        }
    }
}
