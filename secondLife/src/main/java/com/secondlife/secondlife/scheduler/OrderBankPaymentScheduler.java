package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.service.OrderBankPaymentService;
import com.secondlife.secondlife.config.OrderBankTransferProperties;
import com.secondlife.secondlife.repository.DepositRequestRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import java.time.Clock;
@Component @Slf4j
public class OrderBankPaymentScheduler {
    private final DepositRequestRepository requests;
    private final OrderBankPaymentService payments;
    private final OrderBankTransferProperties properties;
    private final Clock clock;
    public OrderBankPaymentScheduler(DepositRequestRepository requests,OrderBankPaymentService payments,
            OrderBankTransferProperties properties,@Qualifier("bankPaymentClock") Clock clock) {
        this.requests=requests;this.payments=payments;this.properties=properties;this.clock=clock;
    }
    @Scheduled(fixedDelayString="${app.orders.bank-transfer.interval-ms}")
    public void expireUnpaidOrders() {
        for(var id:requests.findExpiredOrderIds(clock.instant(),PageRequest.of(0,properties.batchSize()))) {
            try { payments.expire(id); }
            catch(RuntimeException error) { log.warn("Bank payment expiry failed for order {} ({})",id,error.getClass().getSimpleName()); }
        }
    }
}
