package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.OrderAutoReleaseService;

import com.secondlife.secondlife.config.OrderAutoReleaseProperties;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.SettlementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service("orderAutoReleaseService")
public class OrderAutoReleaseServiceImpl implements OrderAutoReleaseService {
    private final OrderRepository orders;
    private final SettlementService settlements;
    private final OrderAutoReleaseProperties properties;
    private final Clock clock;
    private final NotificationService notificationService;

    public OrderAutoReleaseServiceImpl(OrderRepository orders, SettlementService settlements,
            OrderAutoReleaseProperties properties, @Qualifier("orderReleaseClock") Clock clock) {
        this(orders, settlements, properties, clock, null);
    }

    @Autowired
    public OrderAutoReleaseServiceImpl(OrderRepository orders, SettlementService settlements,
            OrderAutoReleaseProperties properties, @Qualifier("orderReleaseClock") Clock clock,
            @Autowired(required = false) NotificationService notificationService) {
        this.orders = orders; this.settlements = settlements; this.properties = properties; this.clock = clock;
        this.notificationService = notificationService;
    }

    public Instant dueCutoff() { return clock.instant().minus(Duration.ofHours(properties.delayHours())); }

    @Transactional
    public boolean releaseIfDue(UUID id) {
        var found = orders.findByIdForUpdate(id);
        if (found.isEmpty()) return false;
        var order = found.get();
        // Recheck after the lock: buyer confirmation, a return or a dispute may have won the race.
        if (order.getStatus() != OrderStatus.DELIVERED || order.getEscrowStatus() != EscrowStatus.HELD
                || order.getShippingDeliveredAt() == null || order.getShippingDeliveredAt().isAfter(dueCutoff())) return false;
        settlements.settle(order);
        if (notificationService != null && order.getSeller() != null) {
            String code = order.getId().toString();
            notificationService.notifyWithEmail(
                order.getSeller().getId(),
                "Giải ngân tiền bán hàng #" + code,
                "Đơn hàng #" + code + " đã hoàn tất 48h tự động giải ngân. Tiền bán hàng đã được chuyển vào ví của bạn.",
                NotificationType.WALLET,
                order.getId(),
                "ORDER",
                "/wallet"
            );
        }
        return true;
    }
}
