package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.commission.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {
    private final OrderRepository orders;
    private final OrderSettlementRepository settlements;
    private final CommissionService commissions;
    private final CommissionCalculator calculator;
    private final WalletService wallets;

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public SettlementResponse settle(Order requestedOrder) {
        // Lock even if invoked by another service: money is released once per order.
        Order order = orders.findByIdForUpdate(requestedOrder.getId()).orElseThrow(() -> new NotFoundException("Order not found"));
        var existing = settlements.findByOrderId(order.getId());
        if (existing.isPresent()) return SettlementResponse.from(existing.get());
        if (order.getStatus() != OrderStatus.DELIVERED || order.getShippingDeliveredAt() == null || order.getEscrowStatus() != EscrowStatus.HELD)
            throw new ConflictException("Settlement requires delivered goods and HELD escrow");
        var snapshot = commissions.requireSnapshot(order.getId());
        if (snapshot.getCommissionBase().compareTo(order.getFinalPrice()) != 0)
            throw new ConflictException("Order price differs from its immutable commission snapshot");
        var amounts = calculator.calculate(snapshot.getCommissionBase(), snapshot.getRate(), snapshot.getMinCommission(), snapshot.getMaxCommission());
        var settlement = new OrderSettlement(); settlement.setOrderId(order.getId()); settlement.setCommissionSnapshotId(snapshot.getId());
        settlement.setBuyerId(order.getBuyer().getId()); settlement.setSellerId(order.getSeller().getId());
        settlement.setCommissionBase(snapshot.getCommissionBase()); settlement.setRawCommission(amounts.rawCommission());
        settlement.setPlatformCommission(amounts.platformCommission()); settlement.setSellerPayout(amounts.sellerPayout());
        settlement.setShippingFee(order.getShippingFee()); settlement.setCurrency(snapshot.getCurrency());
        settlement.setRoundingMode("HALF_UP"); settlement.setSettledAt(Instant.now());
        settlements.saveAndFlush(settlement);
        if (amounts.sellerPayout().signum() > 0) wallets.processEarning(order.getSeller().getId(), amounts.sellerPayout(), order.getId());
        return SettlementResponse.from(settlement);
    }
    @Override @Transactional(readOnly = true)
    public OrderCommissionResponse getCommission(UUID actorId, boolean canReadAny, UUID orderId) {
        authorize(actorId, canReadAny, orderId);
        return commissions.describe(commissions.requireSnapshot(orderId));
    }
    @Override @Transactional(readOnly = true)
    public SettlementResponse getSettlement(UUID actorId, boolean canReadAny, UUID orderId) {
        authorize(actorId, canReadAny, orderId);
        return SettlementResponse.from(settlements.findByOrderId(orderId).orElseThrow(() -> new NotFoundException("Order has not been settled")));
    }
    @Override @Transactional
    public OrderCommissionResponse captureLegacy(UUID actorId, UUID orderId, String reason) {
        var order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED
                || (order.getEscrowStatus() != EscrowStatus.HELD && order.getEscrowStatus() != EscrowStatus.FROZEN))
            throw new ConflictException("Only unsettled orders can capture a commission policy");
        return commissions.describe(commissions.capture(order, actorId, reason));
    }
    private void authorize(UUID actorId, boolean canReadAny, UUID orderId) {
        var order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order not found"));
        if (!canReadAny && !order.getBuyer().getId().equals(actorId) && !order.getSeller().getId().equals(actorId))
            throw new ForbiddenException("You cannot view the finances of this order");
    }
}
