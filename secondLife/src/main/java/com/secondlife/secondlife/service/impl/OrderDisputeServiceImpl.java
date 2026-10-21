package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ReportRequests.Decision;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.Resolve;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.UserReport;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.UserReportMapper;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.UserReportRepository;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.OrderDisputeService;
import com.secondlife.secondlife.service.SellerReputationService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class OrderDisputeServiceImpl implements OrderDisputeService {
    private final OrderRepository orders;
    private final UserReportRepository reports;
    private final ShipmentRepository shipments;
    private static final Duration DISPUTE_WINDOW = Duration.ofHours(48);
    private final Clock clock;
    private final NotificationService notifications;
    private final SellerReputationService reputation;
    private final UserReportMapper reportMapper;
    private final ObjectMapper json;

    public OrderDisputeServiceImpl(OrderRepository orders, UserReportRepository reports,
            ShipmentRepository shipments,
            @Qualifier("orderReleaseClock") Clock clock, NotificationService notifications, SellerReputationService reputation,
            UserReportMapper reportMapper, ObjectMapper json) {
        this.orders = orders;
        this.reports = reports;
        this.shipments = shipments;
        this.clock = clock;
        this.notifications = notifications;
        this.reputation = reputation;
        this.reportMapper = reportMapper;
        this.json = json;
    }

    // Callers lock the order first, consistently with settlement and shipment mutation.
    @Override
    public Instant open(Order order) {
        Instant deliveredAt = order.getShippingDeliveredAt();
        Instant now = clock.instant();
        if (order.getStatus() != OrderStatus.DELIVERED || deliveredAt == null
                || deliveredAt.isAfter(now) || !now.isBefore(deadline(order))) {
            throw new ConflictException("Order disputes must be opened within 48 hours of carrier delivery");
        }
        if (order.getEscrowStatus() != EscrowStatus.HELD && order.getEscrowStatus() != EscrowStatus.FROZEN) {
            throw new ConflictException("Confirmed or settled orders cannot be disputed");
        }
        order.setEscrowStatus(EscrowStatus.FROZEN);
        orders.saveAndFlush(order);
        return now.truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public void resolve(ReportResponse previous, Resolve request, ResponsibleParty party) {
        var report = reports.findById(previous.id(), true).orElseThrow(() -> new NotFoundException("Report not found"));
        ResolutionAction action = request.resolutionAction();
        if (action == ResolutionAction.RETURN_AND_REFUND
                && (report.getOrderId() == null || request.decision() != Decision.CONFIRMED)) {
            throw new BadRequestException("Return and refund requires a confirmed order report");
        }
        Order order = report.getOrderId() == null ? null : orders.findByIdForUpdate(report.getOrderId()).orElseThrow();
        if (action == ResolutionAction.RETURN_AND_REFUND) {
            requireUnsettledDelivery(order);
            if (report.getCreatedAt().isBefore(order.getShippingDeliveredAt())
                    || !report.getCreatedAt().isBefore(deadline(order))) {
                throw new ConflictException("This report was not opened during the order dispute window");
            }
            if (reports.hasApprovedReturn(order.getId(), report.getId())) {
                throw new ConflictException("This order already has an approved return decision");
            }
            report.setReturnApprovedAt(clock.instant());
        }
        report.setStatus(Status.valueOf(request.decision().name()));
        report.setResponsibleParty(party);
        report.setDecisionReason(request.reason().trim());
        report.setResolutionAction(action);
        report.setResolvedAt(clock.instant());
        report.setUpdatedAt(clock.instant());
        reports.saveAndFlush(report);
        if (order != null && action == ResolutionAction.NONE) {
            releaseDisputeHold(order);
        }
    }

    @Override
    public UserReport approvedReturn(Order order, UUID reportId) {
        if (reportId == null) throw new BadRequestException("reportId is required for an approved buyer return");
        var report = reports.findById(reportId, true).orElseThrow(() -> new NotFoundException("Report not found"));
        if (!order.getId().equals(report.getOrderId()) || report.getStatus() != Status.CONFIRMED
                || report.getResolutionAction() != ResolutionAction.RETURN_AND_REFUND
                || report.getReturnApprovedAt() == null) {
            throw new ConflictException("Shipment requires this order's approved return decision");
        }
        return report;
    }

    @Override
    public void bindReturn(Order order, UserReport report, Shipment shipment) {
        if (!order.getId().equals(shipment.getOrderId()) || !"BUYER_TO_SELLER".equals(shipment.getLeg())
                || (shipment.getReportId() != null && !report.getId().equals(shipment.getReportId()))) {
            throw new ConflictException("Return shipment belongs to another decision");
        }
        if (report.getRefundedAt() != null) throw new ConflictException("This return decision has already been refunded");
        UUID previousId = report.getReturnShipmentId();
        if (previousId != null && !previousId.equals(shipment.getId())) {
            var previous = shipments.findById(previousId).orElseThrow();
            if (!"CANCELLED".equals(previous.getStatus())) {
                throw new ConflictException("Cancel or reconcile the existing return shipment before replacing it");
            }
        }
        shipment.setReportId(report.getId());
        shipments.saveAndFlush(shipment);
        report.setReturnShipmentId(shipment.getId());
        report.setUpdatedAt(clock.instant());
        reports.saveAndFlush(report);
    }

    @Override
    public UserReport refundDecision(Order order, Shipment shipment, ResponsibleParty submittedParty) {
        var report = approvedReturn(order, shipment.getReportId());
        if (!shipment.getId().equals(report.getReturnShipmentId())) {
            throw new ConflictException("Refund requires the selected return shipment");
        }
        if (submittedParty != null && submittedParty != report.getResponsibleParty()) {
            throw new ConflictException("Refund responsibility must match the approved dispute decision");
        }
        if (report.getRefundedAt() != null && (order.getEscrowStatus() != EscrowStatus.REFUNDED
                || report.getRefundAmount().compareTo(order.getFinalPrice()) != 0)) {
            throw new ConflictException("Refund audit does not match the order");
        }
        return report;
    }

    @Override
    public void recordRefund(UserReport report, UUID actorId, BigDecimal amount) {
        if (report.getRefundedAt() != null) return;
        report.setRefundedAt(clock.instant());
        report.setRefundedBy(actorId);
        report.setRefundAmount(amount);
        report.setUpdatedAt(clock.instant());
        reports.saveAndFlush(report);
        if (report.getResponsibleParty() == ResponsibleParty.SELLER) {
            reputation.confirmSellerFault(reportMapper.toResponse(report, json), actorId, report.getDecisionReason());
        }
        notifications.notifyInApp(report.getReporterId(), "Đã hoàn tiền tranh chấp",
                "Tiền hàng đã được hoàn vào ví SecondLife của bạn.", NotificationType.REPORT,
                report.getId(), "REPORT", "/reports/" + report.getId());
        notifications.notifyInApp(report.getSellerId(), "Đơn hoàn hàng đã được hoàn tiền",
                "Nhân viên đã hoàn tất xử lý hoàn tiền cho đơn hàng.", NotificationType.REPORT,
                report.getId(), "REPORT", "/seller/orders/" + report.getOrderId());
    }

    private Instant deadline(Order order) {
        return order.getShippingDeliveredAt().plus(DISPUTE_WINDOW);
    }

    private void requireUnsettledDelivery(Order order) {
        if (order.getStatus() != OrderStatus.DELIVERED || order.getShippingDeliveredAt() == null
                || order.getEscrowStatus() != EscrowStatus.FROZEN) {
            throw new ConflictException("Return approval requires delivered goods and frozen escrow");
        }
    }

    private void releaseDisputeHold(Order order) {
        if (order.getStatus() != OrderStatus.DELIVERED || order.getEscrowStatus() != EscrowStatus.FROZEN
                || order.isShippingHold() || reports.hasOrderHold(order.getId())) return;
        boolean shippingHold = shipments.findByOrderIdOrderByCreatedAtAsc(order.getId()).stream()
                .anyMatch(shipment -> ("BUYER_TO_SELLER".equals(shipment.getLeg()) && !"CANCELLED".equals(shipment.getStatus()))
                        || !Set.of("DELIVERED", "CANCELLED").contains(shipment.getStatus()));
        if (!shippingHold) {
            order.setEscrowStatus(EscrowStatus.HELD);
            orders.saveAndFlush(order);
        }
    }
}
