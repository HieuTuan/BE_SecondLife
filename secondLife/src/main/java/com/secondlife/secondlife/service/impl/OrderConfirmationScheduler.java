package com.secondlife.secondlife.service.impl;
import com.secondlife.secondlife.config.OrderConfirmationProperties;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.service.OrderConfirmationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
@Slf4j @Component
@ConditionalOnProperty(name="app.orders.confirmation.enabled",havingValue="true")
public class OrderConfirmationScheduler {
    private final OrderRepository orders;
    private final OrderConfirmationService confirmations;
    private final OrderConfirmationProperties properties;
    public OrderConfirmationScheduler(OrderRepository orders,OrderConfirmationService confirmations,OrderConfirmationProperties properties) {
        this.orders=orders;this.confirmations=confirmations;this.properties=properties;
    }
    @Scheduled(fixedDelayString="${app.orders.confirmation.interval-ms}")
    public void processDueOrders() {
        var cutoff=confirmations.now(); Instant afterDeadline=null; UUID afterId=null;
        while(true) {
            var page=PageRequest.of(0,properties.batchSize());
            var due=afterDeadline==null?orders.findDueConfirmations(OrderStatus.PROCESSING,EscrowStatus.HELD,cutoff,page):
                orders.findDueConfirmationsAfter(OrderStatus.PROCESSING,EscrowStatus.HELD,cutoff,afterDeadline,afterId,page);
            if(due.isEmpty()) return;
            for(var item:due) {
                try { confirmations.processIfDue(item.getId()); }
                catch(RuntimeException error) { log.warn("Confirmation timeout processing failed for order {} ({})",item.getId(),error.getClass().getSimpleName()); }
            }
            // Move past failures so a corrupt/temporarily blocked order cannot starve subsequent batches.
            var last=due.getLast();afterDeadline=last.getDeadline();afterId=last.getId();
        }
    }
}
