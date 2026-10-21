package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ReportRequests.Decision;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.Resolve;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.UserReport;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import com.secondlife.secondlife.mapper.UserReportMapper;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.UserReportRepository;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.SellerReputationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDisputeServiceImplTest {
    private final Instant now = Instant.parse("2026-10-21T10:00:00Z");
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserReportRepository reports = mock(UserReportRepository.class);
    private final ShipmentRepository shipments = mock(ShipmentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final SellerReputationService reputation = mock(SellerReputationService.class);
    private final UserReportMapper mapper = MapperTestSupport.mapper(UserReportMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final OrderDisputeServiceImpl service = new OrderDisputeServiceImpl(orders, reports, shipments,
            Clock.fixed(now, ZoneOffset.UTC),
            notifications, reputation, mapper, json);
    private Order order;
    private UserReport report;

    @BeforeEach void setup() {
        order = new Order(); order.setId(UUID.randomUUID()); order.setStatus(OrderStatus.DELIVERED);
        order.setEscrowStatus(EscrowStatus.HELD); order.setShippingDeliveredAt(now.minus(Duration.ofHours(2)));
        order.setFinalPrice(new BigDecimal("1000000"));
        report = new UserReport(); report.setId(UUID.randomUUID()); report.setOrderId(order.getId());
        report.setReporterId(UUID.randomUUID()); report.setSellerId(UUID.randomUUID());
        report.setStatus(Status.IN_REVIEW); report.setEvidenceUrls("[]");
        report.setCreatedAt(now.minus(Duration.ofHours(1))); report.setReviewerId(UUID.randomUUID());
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(reports.findById(report.getId(), true)).thenReturn(Optional.of(report));
    }

    @Test void acceptsOneNanosecondBeforeDeadlineButRejectsAtDeadline() {
        order.setShippingDeliveredAt(now.minus(Duration.ofHours(48)).plusNanos(1));
        service.open(order); assertEquals(EscrowStatus.FROZEN, order.getEscrowStatus());
        order.setShippingDeliveredAt(now.minus(Duration.ofHours(48)));
        assertThrows(ConflictException.class, () -> service.open(order));
    }

    @Test void acceptedTimestampCannotRoundUpToThePersistedDeadline() {
        Instant deadline = now.plusSeconds(1);
        var precise = new OrderDisputeServiceImpl(orders, reports, shipments,
                Clock.fixed(deadline.minusNanos(1), ZoneOffset.UTC),
                notifications, reputation, mapper, json);
        order.setShippingDeliveredAt(deadline.minus(Duration.ofHours(48)));
        Instant acceptedAt = precise.open(order);
        assertTrue(acceptedAt.isBefore(deadline));
        assertEquals(acceptedAt, acceptedAt.truncatedTo(ChronoUnit.MICROS));
    }

    @ParameterizedTest @EnumSource(value = EscrowStatus.class, names = {"RELEASED", "REFUNDED", "PENDING"})
    void settledOrUnpaidFundsCannotBeDisputed(EscrowStatus status) {
        order.setEscrowStatus(status);
        assertThrows(ConflictException.class, () -> service.open(order));
        verifyNoInteractions(orders);
    }

    @Test void completedOrUndeliveredOrdersCannotBeDisputed() {
        order.setStatus(OrderStatus.COMPLETED);
        assertThrows(ConflictException.class, () -> service.open(order));
        order.setStatus(OrderStatus.DELIVERED); order.setShippingDeliveredAt(null);
        assertThrows(ConflictException.class, () -> service.open(order));
        order.setShippingDeliveredAt(now.plusSeconds(1));
        assertThrows(ConflictException.class, () -> service.open(order));
    }

    @Test void approvesTimelyReportAfterWindowClosesAndKeepsFrozenFunds() {
        order.setShippingDeliveredAt(now.minus(Duration.ofDays(3)));
        report.setCreatedAt(order.getShippingDeliveredAt().plusSeconds(3600)); order.setEscrowStatus(EscrowStatus.FROZEN);
        service.resolve(mapper.toResponse(report, json), approval(), ResponsibleParty.SELLER);
        assertEquals(ResolutionAction.RETURN_AND_REFUND, report.getResolutionAction());
        assertEquals(Status.CONFIRMED, report.getStatus()); assertEquals(now, report.getReturnApprovedAt());
        assertEquals(EscrowStatus.FROZEN, order.getEscrowStatus());
    }

    @Test void cannotApproveSecondReturnOrAnUntimelyHistoricalReport() {
        order.setEscrowStatus(EscrowStatus.FROZEN);
        when(reports.hasApprovedReturn(order.getId(), report.getId())).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.resolve(mapper.toResponse(report, json), approval(), ResponsibleParty.SELLER));
        when(reports.hasApprovedReturn(order.getId(), report.getId())).thenReturn(false);
        report.setCreatedAt(order.getShippingDeliveredAt().minusSeconds(1));
        assertThrows(ConflictException.class, () -> service.resolve(mapper.toResponse(report, json), approval(), ResponsibleParty.SELLER));
    }

    @Test void listingAndDismissedReportsCannotAuthorizeReturn() {
        report.setOrderId(null);
        assertThrows(BadRequestException.class, () -> service.resolve(mapper.toResponse(report, json), approval(), ResponsibleParty.SELLER));
        report.setOrderId(order.getId());
        var invalid = new Resolve(Decision.DISMISSED, ResponsibleParty.NONE, "Rejected", ResolutionAction.RETURN_AND_REFUND);
        assertThrows(BadRequestException.class, () -> service.resolve(mapper.toResponse(report, json), invalid, ResponsibleParty.NONE));
    }

    @Test void dismissalReleasesOnlyTheDisputeHold() {
        order.setEscrowStatus(EscrowStatus.FROZEN);
        service.resolve(mapper.toResponse(report, json), new Resolve(Decision.DISMISSED, ResponsibleParty.NONE, "Rejected"), ResponsibleParty.NONE);
        assertEquals(EscrowStatus.HELD, order.getEscrowStatus());
    }

    @Test void dismissalCannotReleaseAnotherReportOrShippingHold() {
        order.setEscrowStatus(EscrowStatus.FROZEN);
        when(reports.hasOrderHold(order.getId())).thenReturn(true);
        service.resolve(mapper.toResponse(report, json), new Resolve(Decision.DISMISSED, ResponsibleParty.NONE, "Rejected"), ResponsibleParty.NONE);
        assertEquals(EscrowStatus.FROZEN, order.getEscrowStatus());
        when(reports.hasOrderHold(order.getId())).thenReturn(false);
        var shipment = new Shipment(); shipment.setLeg("SELLER_TO_BUYER"); shipment.setStatus("RETURNED");
        when(shipments.findByOrderIdOrderByCreatedAtAsc(order.getId())).thenReturn(List.of(shipment));
        service.resolve(mapper.toResponse(report, json), new Resolve(Decision.DISMISSED, ResponsibleParty.NONE, "Rejected"), ResponsibleParty.NONE);
        assertEquals(EscrowStatus.FROZEN, order.getEscrowStatus());
    }

    @Test void dismissalCannotClearPersistedPodOrCarrierHoldEvenWhenShipmentIsDelivered() {
        order.setEscrowStatus(EscrowStatus.FROZEN); order.setShippingHold(true);
        var shipment = new Shipment(); shipment.setLeg("SELLER_TO_BUYER"); shipment.setStatus("DELIVERED");
        when(shipments.findByOrderIdOrderByCreatedAtAsc(order.getId())).thenReturn(List.of(shipment));
        service.resolve(mapper.toResponse(report, json), new Resolve(Decision.DISMISSED, ResponsibleParty.NONE, "Rejected"), ResponsibleParty.NONE);
        assertEquals(EscrowStatus.FROZEN, order.getEscrowStatus());
        assertTrue(order.isShippingHold());
    }

    @Test void shipmentMustUseAnApprovedReportForItsOwnOrder() {
        assertThrows(BadRequestException.class, () -> service.approvedReturn(order, null));
        assertThrows(ConflictException.class, () -> service.approvedReturn(order, report.getId()));
        approve(); report.setOrderId(UUID.randomUUID());
        assertThrows(ConflictException.class, () -> service.approvedReturn(order, report.getId()));
    }

    @Test void uncertainReturnCannotBeReplacedButCancelledReturnCan() {
        approve(); var old = returnedShipment(); old.setStatus("CREATION_UNCERTAIN");
        report.setReturnShipmentId(old.getId()); when(shipments.findById(old.getId())).thenReturn(Optional.of(old));
        var replacement = returnedShipment();
        assertThrows(ConflictException.class, () -> service.bindReturn(order, report, replacement));
        old.setStatus("CANCELLED"); service.bindReturn(order, report, replacement);
        assertEquals(replacement.getId(), report.getReturnShipmentId());
        assertEquals(report.getId(), replacement.getReportId());
    }

    @Test void refundUsesSelectedShipmentAndStoredResponsibility() {
        approve(); var shipment = returnedShipment(); report.setReturnShipmentId(shipment.getId());
        assertSame(report, service.refundDecision(order, shipment, null));
        assertThrows(ConflictException.class, () -> service.refundDecision(order, shipment, ResponsibleParty.BUYER));
        report.setReturnShipmentId(UUID.randomUUID());
        assertThrows(ConflictException.class, () -> service.refundDecision(order, shipment, null));
    }

    @Test void refundAuditAndSellerFaultAreRecordedOnce() {
        approve(); UUID actor = UUID.randomUUID();
        service.recordRefund(report, actor, order.getFinalPrice());
        service.recordRefund(report, actor, order.getFinalPrice());
        assertEquals(now, report.getRefundedAt()); assertEquals(actor, report.getRefundedBy());
        assertEquals(order.getFinalPrice(), report.getRefundAmount());
        verify(reputation).confirmSellerFault(any(), eq(actor), any());
        verify(notifications, times(2)).notifyInApp(any(), anyString(), anyString(), any(NotificationType.class), any(), anyString(), anyString());
    }

    private Resolve approval() {
        return new Resolve(Decision.CONFIRMED, ResponsibleParty.SELLER, "Approved return", ResolutionAction.RETURN_AND_REFUND);
    }
    private void approve() {
        report.setStatus(Status.CONFIRMED); report.setResolutionAction(ResolutionAction.RETURN_AND_REFUND);
        report.setReturnApprovedAt(now); report.setResponsibleParty(ResponsibleParty.SELLER); report.setDecisionReason("Approved return");
    }
    private Shipment returnedShipment() {
        var shipment = new Shipment(); shipment.setId(UUID.randomUUID()); shipment.setOrderId(order.getId());
        shipment.setReportId(report.getId()); shipment.setLeg("BUYER_TO_SELLER"); shipment.setStatus("DELIVERED");
        return shipment;
    }
}
