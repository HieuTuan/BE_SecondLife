package com.secondlife.secondlife.service;

import com.secondlife.secondlife.config.OrderAutoReleaseProperties;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.DueSettlementOrder;
import com.secondlife.secondlife.service.impl.OrderAutoReleaseScheduler;
import com.secondlife.secondlife.service.impl.OrderAutoReleaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderAutoReleaseTest {
    private final Instant now = Instant.parse("2026-10-13T10:00:00Z");
    private final OrderRepository orders = mock(OrderRepository.class);
    private final SettlementService settlements = mock(SettlementService.class);
    private final OrderAutoReleaseProperties properties = new OrderAutoReleaseProperties(true, 48, 60000, 100);
    private final OrderAutoReleaseService service = new OrderAutoReleaseService(orders, settlements, properties,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test void releasesAtExactly48HoursButNotOneNanosecondEarlier() {
        var order = delivered();
        order.setShippingDeliveredAt(now.minus(Duration.ofHours(48)).plusNanos(1));
        assertFalse(service.releaseIfDue(order.getId()));
        verifyNoInteractions(settlements);
        order.setShippingDeliveredAt(now.minus(Duration.ofHours(48)));
        assertTrue(service.releaseIfDue(order.getId()));
        verify(settlements).settle(order);
    }

    @ParameterizedTest @EnumSource(value = EscrowStatus.class, names = {"FROZEN", "REFUNDED", "RELEASED"})
    void skipsDisputedReturnedAndReleasedEscrow(EscrowStatus status) {
        var order = delivered(); order.setEscrowStatus(status);
        assertFalse(service.releaseIfDue(order.getId()));
        verifyNoInteractions(settlements);
    }

    @ParameterizedTest @EnumSource(value = OrderStatus.class, names = {"DELIVERED"}, mode = EnumSource.Mode.EXCLUDE)
    void skipsOrdersThatAreNotDelivered(OrderStatus status) {
        var order = delivered(); order.setStatus(status);
        assertFalse(service.releaseIfDue(order.getId()));
        verifyNoInteractions(settlements);
    }

    @Test void missingCarrierDeliveryTimeCannotReleaseMoney() {
        var order = delivered(); order.setShippingDeliveredAt(null);
        assertFalse(service.releaseIfDue(order.getId()));
        verifyNoInteractions(settlements);
    }

    @Test void aDeletedOrderIsIgnored() {
        assertFalse(service.releaseIfDue(UUID.randomUUID()));
        verifyNoInteractions(settlements);
    }

    @Test void failedOrderDoesNotPreventNextOrderInTheBatchFromSettling() {
        var release = mock(OrderAutoReleaseService.class);
        UUID failed = UUID.randomUUID(), eligible = UUID.randomUUID();
        when(release.dueCutoff()).thenReturn(now.minus(Duration.ofHours(48)));
        var due = mock(DueSettlementOrder.class); when(due.getId()).thenReturn(failed); when(due.getDeliveredAt()).thenReturn(now);
        var next = mock(DueSettlementOrder.class); when(next.getId()).thenReturn(eligible); when(next.getDeliveredAt()).thenReturn(now);
        when(orders.findDueSettlementOrders(eq(OrderStatus.DELIVERED), eq(EscrowStatus.HELD), any(), any()))
                .thenReturn(List.of(due));
        when(orders.findDueSettlementOrdersAfter(eq(OrderStatus.DELIVERED), eq(EscrowStatus.HELD), any(), eq(now), eq(failed), any()))
                .thenReturn(List.of(next));
        when(release.releaseIfDue(failed)).thenThrow(new IllegalStateException("Cannot settle"));
        new OrderAutoReleaseScheduler(orders, release, properties).releaseDueOrders();
        verify(release).releaseIfDue(eligible);
    }

    private Order delivered() {
        var order = new Order(); order.setId(UUID.randomUUID()); order.setStatus(OrderStatus.DELIVERED);
        order.setEscrowStatus(EscrowStatus.HELD); order.setShippingDeliveredAt(now.minus(Duration.ofHours(49)));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        return order;
    }
}
