package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.dto.response.ReturnShippingDeductionResponse;
import com.secondlife.secondlife.dto.response.ReturnShippingFeeResponse;
import com.secondlife.secondlife.dto.response.SellerReturnShippingDebtResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.ReturnShippingFee;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.UserReport;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.enums.ReturnShippingFeeStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.ReturnShippingFeeMapper;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.ReturnShippingFeeRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.service.ReturnShippingFeeService;
import com.secondlife.secondlife.service.SystemWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReturnShippingFeeServiceImpl implements ReturnShippingFeeService {
    private static final BigDecimal MAX_FEE = new BigDecimal("1000000000");
    private final ReturnShippingFeeRepository fees;
    private final ShipmentRepository shipments;
    private final OrderRepository orders;
    private final SystemWalletService systemWallet;
    private final ReturnShippingFeeMapper mapper;

    // Refund callers already hold the order, shipment and approved report locks.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordForRefund(Order order, Shipment shipment, UserReport report, UUID actorId) {
        if (!sellerFault(report)) return;
        requireEligible(order, shipment, report);
        var existing = fees.findById(shipment.getId());
        if (existing.isPresent()) {
            collectPending(existing.get(), order, shipment, report, actorId);
            return;
        }
        var fee = new ReturnShippingFee();
        fee.setId(shipment.getId());
        fee.setOrderId(order.getId());
        fee.setReportId(report.getId());
        fee.setSellerId(order.getSeller().getId());
        assess(fee, order, shipment, actorId);
        fees.saveAndFlush(fee);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void collectPendingForRefund(Order order, Shipment shipment, UserReport report, UUID actorId) {
        fees.findById(shipment.getId()).ifPresent(fee -> collectPending(fee, order, shipment, report, actorId));
    }

    @Override
    @Transactional
    public void collectOutstanding(UUID shipmentId) {
        var fee = fees.lockByShipmentId(shipmentId).orElse(null);
        if (fee == null || fee.getStatus() != ReturnShippingFeeStatus.OUTSTANDING) return;

        var order = orders.findById(fee.getOrderId()).orElseThrow(() -> new NotFoundException("Order not found"));
        if (!fee.getSellerId().equals(order.getSeller().getId())) {
            throw new ConflictException("Return shipping fee does not belong to the order seller");
        }
        var collected = collectFromSeller(order, fee.getDebtAmount(), shipmentId);
        if (collected.signum() == 0) return;

        var remaining = fee.getDebtAmount().subtract(collected);
        fee.setCollectedAmount(fee.getCollectedAmount().add(collected));
        fee.setDebtAmount(remaining);
        fee.setStatus(remaining.signum() == 0 ? ReturnShippingFeeStatus.PAID : ReturnShippingFeeStatus.OUTSTANDING);
        fee.setLastCollectedAt(Instant.now());
        fees.saveAndFlush(fee);
    }

    @Override
    @Transactional(readOnly = true)
    public ReturnShippingFeeResponse get(UUID actorId, boolean reviewer, UUID shipmentId) {
        var shipment = shipments.findById(shipmentId).orElseThrow(() -> new NotFoundException("Shipment not found"));
        if (!reviewer && !Objects.equals(actorId, shipment.getSellerId())) {
            throw new ForbiddenException("Only this seller or an authorized reviewer can read return shipping fees");
        }
        return mapper.toResponse(fees.findById(shipmentId)
                .orElseThrow(() -> new NotFoundException("Return shipping fee assessment not found")));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnShippingFeeResponse> mine(UUID sellerId, boolean outstandingOnly, Pageable pageable) {
        var page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = outstandingOnly
                ? fees.findBySellerIdAndStatus(sellerId, ReturnShippingFeeStatus.OUTSTANDING, page)
                : fees.findBySellerId(sellerId, page);
        return PageResponse.from(result.map(mapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReturnShippingDeductionResponse> deductions(UUID sellerId, boolean outstandingOnly, Pageable pageable) {
        var page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return PageResponse.from(fees.findDeductions(sellerId, outstandingOnly, page));
    }

    @Override
    @Transactional(readOnly = true)
    public SellerReturnShippingDebtResponse debt(UUID sellerId) {
        return new SellerReturnShippingDebtResponse(sellerId, fees.outstandingDebt(sellerId));
    }

    private void collectPending(ReturnShippingFee fee, Order order, Shipment shipment, UserReport report, UUID actorId) {
        if (fee.getStatus() != ReturnShippingFeeStatus.PENDING_FEE) return;
        requireEligible(order, shipment, report);
        if (!order.getId().equals(fee.getOrderId()) || !report.getId().equals(fee.getReportId())
                || !order.getSeller().getId().equals(fee.getSellerId())) {
            throw new ConflictException("Return shipping fee assessment does not match the refund");
        }
        if (shipment.getActualFee() == null) return;
        assess(fee, order, shipment, actorId);
        fees.saveAndFlush(fee);
    }

    private void assess(ReturnShippingFee fee, Order order, Shipment shipment, UUID actorId) {
        if (shipment.getActualFee() == null) return;
        var amount = money(shipment.getActualFee());
        var collected = collectFromSeller(order, amount, shipment.getId());
        var debt = amount.subtract(collected);
        fee.setFeeAmount(amount);
        fee.setCollectedAmount(collected);
        fee.setDebtAmount(debt);
        fee.setStatus(debt.signum() == 0 ? ReturnShippingFeeStatus.PAID : ReturnShippingFeeStatus.OUTSTANDING);
        var now = Instant.now();
        fee.setAssessedAt(now);
        fee.setAssessedBy(actorId);
        if (collected.signum() > 0) fee.setLastCollectedAt(now);
    }

    private BigDecimal collectFromSeller(Order order, BigDecimal amount, UUID shipmentId) {
        var collected = money(systemWallet.collectReturnShippingFee(order, amount, shipmentId));
        if (collected.compareTo(amount) > 0) {
            throw new ConflictException("Collected return shipping fee exceeds the amount due");
        }
        return collected;
    }

    private boolean sellerFault(UserReport report) {
        return report != null && report.getStatus() == Status.CONFIRMED
                && report.getResolutionAction() == ResolutionAction.RETURN_AND_REFUND
                && report.getResponsibleParty() == ResponsibleParty.SELLER;
    }

    private void requireEligible(Order order, Shipment shipment, UserReport report) {
        if (!sellerFault(report) || order.getStatus() != OrderStatus.CANCELLED
                || order.getEscrowStatus() != EscrowStatus.REFUNDED || report.getReturnApprovedAt() == null
                || !"BUYER_TO_SELLER".equals(shipment.getLeg()) || !"DELIVERED".equals(shipment.getStatus())
                || !order.getId().equals(shipment.getOrderId()) || !order.getId().equals(report.getOrderId())
                || !report.getId().equals(shipment.getReportId())
                || !shipment.getId().equals(report.getReturnShipmentId())
                || !order.getSeller().getId().equals(shipment.getSellerId())
                || !order.getSeller().getId().equals(report.getSellerId())) {
            throw new ConflictException("Return shipping fee requires this refunded order's approved seller-fault return");
        }
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_FEE) > 0) {
            throw new ConflictException("Invalid return shipping fee amount");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException invalidScale) {
            throw new ConflictException("Invalid return shipping fee amount");
        }
    }
}
