package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse;
import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse.CollectionStatus;
import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse.Payment;
import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse.ReturnReserve;
import com.secondlife.secondlife.dto.response.AdminInspectionFundsResponse.ReturnReserveStatus;
import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.AdminInspectionFundsMapper;
import com.secondlife.secondlife.repository.InspectionFeeSettlementRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.service.AdminInspectionFundsService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminInspectionFundsServiceImpl implements AdminInspectionFundsService {
    private final InspectionOrderRepository orders;
    private final InspectionFeeSettlementRepository settlements;
    private final ShipmentRepository shipments;
    private final AdminInspectionFundsMapper mapper;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminInspectionFundsResponse getFunds(UUID inspectionOrderId) {
        InspectionOrder order = orders.findById(inspectionOrderId)
                .orElseThrow(() -> new NotFoundException("Đơn kiểm định không tồn tại"));
        InspectionFeeSettlement settlement = settlements.findByInspectionOrderId(inspectionOrderId).orElse(null);
        List<Shipment> route = shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspectionOrderId);

        BigDecimal deposit = amount(order.getBuyerCommitmentDeposit());
        Payment buyer = payment(amount(order.getBuyerFeeShare()).add(deposit), order.getBuyerPaidAt());
        Payment seller = payment(amount(order.getSellerFeeShare()), order.getSellerPaidAt());
        BigDecimal totalCollected = settlement == null
                ? buyer.amountCollected().add(seller.amountCollected()) : settlement.getTotalCollected();
        InspectionFeePayer feePayer = order.getFeePayer() == null ? InspectionFeePayer.BUYER_PAYS : order.getFeePayer();
        BigDecimal shippingFee = amount(order.getShippingFee());
        BigDecimal outboundCashPending = outboundCashPending(order, settlement, route, feePayer, shippingFee);
        ReturnReserve reserve = returnReserve(order, settlement, route, feePayer);
        BigDecimal unsettled = settlement == null
                ? totalCollected.subtract(releasedOutboundCash(order, route, feePayer, shippingFee)).max(BigDecimal.ZERO)
                : reserve.heldAmount().add(outboundCashPending);

        return new AdminInspectionFundsResponse(inspectionOrderId, order.getStatus(), order.getInspectionScope(),
                order.getOrderType(), feePayer, order.isCashShippingSimulation(), "VND", amount(order.getInspectionFee()),
                amount(order.getTravelFee()), shippingFee, deposit, buyer, seller, collectionStatus(order, buyer, seller),
                totalCollected, unsettled, outboundCashPending, reserve, mapper.toSettlement(settlement),
                route.stream().map(mapper::toShipping).toList());
    }

    private Payment payment(BigDecimal due, Instant paidAt) {
        return new Payment(due, paidAt == null ? BigDecimal.ZERO : due, paidAt);
    }

    private CollectionStatus collectionStatus(InspectionOrder order, Payment buyer, Payment seller) {
        if ("WAITING_SELLER_APPROVAL".equals(order.getStatus()) || "WAITING_BUYER_APPROVAL".equals(order.getStatus())) {
            return CollectionStatus.NOT_DUE;
        }
        BigDecimal due = buyer.amountDue().add(seller.amountDue());
        BigDecimal collected = buyer.amountCollected().add(seller.amountCollected());
        if (due.signum() == 0) return CollectionStatus.NO_PAYMENT_REQUIRED;
        if (collected.signum() == 0) return CollectionStatus.UNPAID;
        return collected.compareTo(due) < 0 ? CollectionStatus.PARTIALLY_PAID : CollectionStatus.PAID;
    }

    private BigDecimal outboundCashPending(InspectionOrder order, InspectionFeeSettlement settlement,
                                           List<Shipment> route, InspectionFeePayer feePayer, BigDecimal shippingFee) {
        if (!order.isCashShippingSimulation() || order.getBuyerPaidAt() == null || outboundCashConfirmed(route)) {
            return BigDecimal.ZERO;
        }
        return settlement == null ? feePayer.buyerShare(shippingFee) : settlement.getOutboundShippingAllocated();
    }

    private BigDecimal releasedOutboundCash(InspectionOrder order, List<Shipment> route,
                                             InspectionFeePayer feePayer, BigDecimal shippingFee) {
        // Confirmation resolves the full buyer-funded amount through reimbursement and any excess refund.
        return order.isCashShippingSimulation() && outboundCashConfirmed(route)
                ? feePayer.buyerShare(shippingFee) : BigDecimal.ZERO;
    }

    private boolean outboundCashConfirmed(List<Shipment> route) {
        return route.stream().anyMatch(shipment -> "SELLER_TO_CENTER".equals(shipment.getLeg())
                && shipment.getCashConfirmedAt() != null);
    }

    private ReturnReserve returnReserve(InspectionOrder order, InspectionFeeSettlement settlement,
                                        List<Shipment> route, InspectionFeePayer feePayer) {
        BigDecimal planned = amount(order.getReturnShippingReserveFee());
        if (planned.signum() == 0) {
            return new ReturnReserve(planned, BigDecimal.ZERO, ReturnReserveStatus.NOT_REQUIRED);
        }
        BigDecimal collectedReserve = collectedReturnReserve(order, feePayer, planned);
        if (settlement == null) {
            return new ReturnReserve(planned, collectedReserve, collectedReserve.signum() == 0
                    ? ReturnReserveStatus.NOT_COLLECTED : ReturnReserveStatus.HELD_PENDING_INSPECTION);
        }
        if (settlement.getReturnShippingSettledAt() != null) {
            return new ReturnReserve(planned, BigDecimal.ZERO, ReturnReserveStatus.SETTLED);
        }
        return switch (settlement.getOutcome()) {
            case PAYMENT_EXPIRED, CANCELLED_BEFORE_INSPECTION, PASSED_PURCHASED ->
                    new ReturnReserve(planned, BigDecimal.ZERO, collectedReserve.signum() == 0
                            ? ReturnReserveStatus.NOT_COLLECTED : ReturnReserveStatus.REFUNDED_UNUSED);
            case FAILED_INSPECTION, PASSED_BUYER_DEFAULTED -> {
                BigDecimal held = settlement.getReturnReserveAllocated();
                Shipment returning = route.reversed().stream()
                        .filter(shipment -> "CENTER_TO_SELLER".equals(shipment.getLeg())
                                && !"CANCELLED".equals(shipment.getStatus()) && !"FAILED".equals(shipment.getStatus()))
                        .findFirst().orElse(null);
                yield new ReturnReserve(planned, held, pendingReturnStatus(returning, held, order.isCashShippingSimulation()));
            }
        };
    }

    private BigDecimal collectedReturnReserve(InspectionOrder order, InspectionFeePayer feePayer, BigDecimal planned) {
        BigDecimal buyerShare = feePayer.buyerShare(planned);
        BigDecimal fromBuyer = order.getBuyerPaidAt() == null
                ? BigDecimal.ZERO : buyerShare.min(amount(order.getBuyerFeeShare()));
        BigDecimal fromSeller = order.getSellerPaidAt() == null
                ? BigDecimal.ZERO : planned.subtract(buyerShare).min(amount(order.getSellerFeeShare()));
        return fromBuyer.add(fromSeller);
    }

    private ReturnReserveStatus pendingReturnStatus(Shipment shipment, BigDecimal held, boolean cashShipping) {
        if (shipment == null) return ReturnReserveStatus.AWAITING_RETURN_SHIPMENT;
        if (!"DELIVERED".equals(shipment.getStatus())) return ReturnReserveStatus.RETURN_IN_PROGRESS;
        if (shipment.getActualFee() == null) return ReturnReserveStatus.AWAITING_ACTUAL_FEE;
        if (shipment.getActualFee().compareTo(held) > 0) return ReturnReserveStatus.EXCEEDS_RESERVE;
        return cashShipping ? ReturnReserveStatus.AWAITING_CASH_CONFIRMATION : ReturnReserveStatus.AWAITING_RECONCILIATION;
    }

    private BigDecimal amount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
