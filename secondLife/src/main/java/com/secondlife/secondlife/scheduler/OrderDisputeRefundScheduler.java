package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderDisputeRefundScheduler {
    private static final int BATCH_SIZE = 50;

    private final ShipmentRepository shipments;
    private final ShipmentService shipmentService;
    private UUID afterId;

    @Scheduled(fixedDelayString = "${app.orders.dispute-refund.interval-ms:10000}",
            initialDelayString = "${app.orders.dispute-refund.interval-ms:10000}")
    public void refundDeliveredReturns() {
        var delivered = shipments.findDeliveredDisputeReturns(afterId, PageRequest.of(0, BATCH_SIZE));
        // Continue past failed rows and revisit them after reaching the end of the batch sequence.
        afterId = delivered.size() < BATCH_SIZE ? null : delivered.getLast();
        for (UUID shipmentId : delivered) {
            try {
                shipmentService.refundDeliveredDisputeReturn(shipmentId);
            } catch (RuntimeException error) {
                log.warn("Automatic dispute refund failed for shipment {} ({})",
                        shipmentId, error.getClass().getSimpleName());
            }
        }
    }
}
