package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.WalletTransactionReferenceType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.InspectionFeeSettlementRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionFeeSettlementService;
import com.secondlife.secondlife.service.WalletService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InspectionFeeSettlementServiceImpl implements InspectionFeeSettlementService {

    private final InspectionFeeSettlementRepository settlements;
    private final InspectionOrderRepository inspectionOrders;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final BigDecimal platformFeeRate;

    public InspectionFeeSettlementServiceImpl(
            InspectionFeeSettlementRepository settlements,
            InspectionOrderRepository inspectionOrders,
            WalletService walletService,
            UserRepository userRepository,
            @Value("${app.inspection.platform-fee-share-rate:0.00}") BigDecimal platformFeeRate) {
        this.settlements = settlements;
        this.inspectionOrders = inspectionOrders;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.platformFeeRate = platformFeeRate != null ? platformFeeRate : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getCenterPayouts(UUID centerId, Collection<UUID> inspectionOrderIds) {
        if (inspectionOrderIds.isEmpty()) {
            return Map.of();
        }
        return settlements.findCenterPayouts(centerId, inspectionOrderIds).stream()
                .collect(Collectors.toMap(
                        InspectionFeeSettlementRepository.CenterPayout::getInspectionOrderId,
                        InspectionFeeSettlementRepository.CenterPayout::getAmount));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public InspectionFeeSettlement settle(InspectionOrder inspectionOrder, InspectionSettlementOutcome outcome, String reason) {
        Objects.requireNonNull(inspectionOrder, "inspectionOrder must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");

        var existing = settlements.findByInspectionOrderId(inspectionOrder.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        BigDecimal buyerFee = inspectionOrder.getBuyerPaidAt() != null && inspectionOrder.getBuyerFeeShare() != null
                ? inspectionOrder.getBuyerFeeShare() : BigDecimal.ZERO;
        BigDecimal buyerDeposit = inspectionOrder.getBuyerPaidAt() != null && inspectionOrder.getBuyerCommitmentDeposit() != null
                ? inspectionOrder.getBuyerCommitmentDeposit() : BigDecimal.ZERO;
        BigDecimal sellerFee = inspectionOrder.getSellerPaidAt() != null && inspectionOrder.getSellerFeeShare() != null
                ? inspectionOrder.getSellerFeeShare() : BigDecimal.ZERO;

        BigDecimal totalCollected = buyerFee.add(buyerDeposit).add(sellerFee);

        User centerRecipient = resolveCenterRecipient(inspectionOrder);

        BigDecimal centerPayout = BigDecimal.ZERO;
        BigDecimal outboundShipping = BigDecimal.ZERO;
        BigDecimal returnReserve = BigDecimal.ZERO;
        BigDecimal buyerRefund = BigDecimal.ZERO;
        BigDecimal sellerRefund = BigDecimal.ZERO;
        BigDecimal sellerCompensation = BigDecimal.ZERO;

        BigDecimal serviceFee = (inspectionOrder.getInspectionFee() != null ? inspectionOrder.getInspectionFee() : BigDecimal.ZERO)
                .add(inspectionOrder.getTravelFee() != null ? inspectionOrder.getTravelFee() : BigDecimal.ZERO);
        BigDecimal shippingFee = inspectionOrder.getShippingFee() != null ? inspectionOrder.getShippingFee() : BigDecimal.ZERO;
        BigDecimal outboundPrepaid = inspectionOrder.isCashShippingSimulation()
                ? buyerShare(inspectionOrder, shippingFee) : shippingFee;
        BigDecimal returnReserveFee = inspectionOrder.getReturnShippingReserveFee() != null ? inspectionOrder.getReturnShippingReserveFee() : BigDecimal.ZERO;

        switch (outcome) {
            case PAYMENT_EXPIRED, CANCELLED_BEFORE_INSPECTION -> {
                buyerRefund = buyerFee.add(buyerDeposit);
                sellerRefund = sellerFee;
            }
            case FAILED_INSPECTION -> {
                centerPayout = computeCenterPayout(inspectionOrder, serviceFee);
                outboundShipping = outboundPrepaid;
                returnReserve = returnReserveFee;
                buyerRefund = buyerDeposit;
            }
            case PASSED_PURCHASED -> {
                centerPayout = computeCenterPayout(inspectionOrder, serviceFee);
                outboundShipping = outboundPrepaid;
                BigDecimal buyerReserveShare = buyerShare(inspectionOrder, returnReserveFee);
                buyerRefund = buyerDeposit.add(buyerReserveShare);
                sellerRefund = returnReserveFee.subtract(buyerReserveShare);
            }
            case PASSED_BUYER_DEFAULTED -> {
                centerPayout = computeCenterPayout(inspectionOrder, serviceFee);
                outboundShipping = outboundPrepaid;
                returnReserve = returnReserveFee;
                sellerCompensation = buyerDeposit;
            }
        }

        BigDecimal allocatedBeforePlatform = centerPayout
                .add(outboundShipping)
                .add(returnReserve)
                .add(buyerRefund)
                .add(sellerRefund)
                .add(sellerCompensation);

        BigDecimal platformRetained = totalCollected.subtract(allocatedBeforePlatform);
        if (platformRetained.signum() < 0) {
            throw new ConflictException("Phí đã thu không đủ để quyết toán kiểm định; cần kiểm tra giao dịch trước khi xử lý");
        }

        // A failed wallet operation must roll back the entire settlement.
        if (centerPayout.signum() > 0) {
            if (centerRecipient == null) throw new ConflictException("Chưa xác định được trung tâm nhận tiền kiểm định");
            walletService.processEarning(centerRecipient.getId(), centerPayout, inspectionOrder.getId(),
                    WalletTransactionReferenceType.INSPECTION_ORDER);
        }
        if (buyerRefund.signum() > 0 && inspectionOrder.getBuyer() != null) {
            walletService.processRefund(inspectionOrder.getBuyer().getId(), buyerRefund, inspectionOrder.getId(),
                    WalletTransactionReferenceType.INSPECTION_ORDER);
        }
        if (sellerRefund.signum() > 0 && inspectionOrder.getSeller() != null) {
            walletService.processRefund(inspectionOrder.getSeller().getId(), sellerRefund, inspectionOrder.getId(),
                    WalletTransactionReferenceType.INSPECTION_ORDER);
        }
        if (sellerCompensation.signum() > 0 && inspectionOrder.getSeller() != null) {
            walletService.processEarning(inspectionOrder.getSeller().getId(), sellerCompensation, inspectionOrder.getId(),
                    WalletTransactionReferenceType.INSPECTION_ORDER);
        }

        var settlement = new InspectionFeeSettlement();
        settlement.setInspectionOrder(inspectionOrder);
        settlement.setOutcome(outcome);
        settlement.setTotalCollected(totalCollected);
        settlement.setCenterRecipientUser(centerRecipient);
        settlement.setCenterPayoutAmount(centerPayout);
        settlement.setOutboundShippingAllocated(outboundShipping);
        settlement.setReturnReserveAllocated(returnReserve);
        settlement.setBuyerRefundAmount(buyerRefund);
        settlement.setSellerRefundAmount(sellerRefund);
        settlement.setSellerCompensationAmount(sellerCompensation);
        settlement.setPlatformRetainedAmount(platformRetained);
        settlement.setSettledAt(Instant.now());

        settlement = settlements.saveAndFlush(settlement);

        inspectionOrder.setFeeSettledAt(settlement.getSettledAt());
        inspectionOrders.save(inspectionOrder);

        return settlement;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleReturnShipping(InspectionOrder order, BigDecimal actualFee) {
        // The shipment workflow locks the inspection before changing its settlement.
        var settlement = settlements.findByInspectionOrderId(order.getId()).orElse(null);
        if (!hasUnsettledReturnReserve(settlement)) {
            return;
        }

        BigDecimal reserve = settlement.getReturnReserveAllocated();
        BigDecimal remainder = reserve.subtract(actualFee);
        if (remainder.signum() < 0) {
            log.warn("Inspection {} return shipping fee {} exceeds reserve {}", order.getId(), actualFee, reserve);
            return;
        }

        applyReturnShippingSettlement(order, settlement, actualFee);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal reimburseCashShipping(InspectionOrder order, Shipment shipment, BigDecimal paidAmount) {
        if (!order.isCashShippingSimulation() || order.getBuyerPaidAt() == null || order.getSellerPaidAt() == null) {
            throw new ConflictException("Đơn chưa đủ điều kiện quyết toán cước tiền mặt mô phỏng");
        }

        var settlement = settlements.findByInspectionOrderId(order.getId()).orElse(null);
        if ("SELLER_TO_CENTER".equals(shipment.getLeg())) {
            if (settlement != null && (settlement.getOutcome() == InspectionSettlementOutcome.PAYMENT_EXPIRED
                    || settlement.getOutcome() == InspectionSettlementOutcome.CANCELLED_BEFORE_INSPECTION)) {
                throw new ConflictException("Phần cước đã được hoàn khi đóng đơn kiểm định");
            }
            BigDecimal prepaid = buyerShare(order, order.getShippingFee());
            BigDecimal reimbursement = buyerShare(order, paidAmount);
            if (reimbursement.compareTo(prepaid) > 0) {
                throw new ConflictException("Phần cước thực tế của người mua vượt khoản đã giữ");
            }
            BigDecimal remainder = prepaid.subtract(reimbursement);
            if (remainder.signum() > 0) {
                walletService.processRefund(order.getBuyer().getId(), remainder, shipment.getId(),
                        WalletTransactionReferenceType.SHIPMENT);
            }
            reimburse(shipment, reimbursement);
            return reimbursement;
        }

        if (!"CENTER_TO_SELLER".equals(shipment.getLeg()) || !hasUnsettledReturnReserve(settlement)) {
            throw new ConflictException("Không có khoản dự phòng chặng hoàn chờ quyết toán");
        }
        if (paidAmount.compareTo(settlement.getReturnReserveAllocated()) > 0) {
            throw new ConflictException("Cước thực tế vượt khoản dự phòng chặng hoàn đã thu");
        }
        reimburse(shipment, paidAmount);
        applyReturnShippingSettlement(order, settlement, paidAmount);
        return paidAmount;
    }

    private void reimburse(Shipment shipment, BigDecimal amount) {
        if (amount.signum() > 0) {
            walletService.reimburseShipping(shipment.getCashPayerId(), amount, shipment.getId());
        }
    }

    private boolean hasUnsettledReturnReserve(InspectionFeeSettlement settlement) {
        return settlement != null && settlement.getReturnShippingSettledAt() == null
                && (settlement.getOutcome() == InspectionSettlementOutcome.FAILED_INSPECTION
                    || settlement.getOutcome() == InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED);
    }

    private void applyReturnShippingSettlement(InspectionOrder order, InspectionFeeSettlement settlement,
                                               BigDecimal actualFee) {
        BigDecimal reserve = settlement.getReturnReserveAllocated();
        BigDecimal remainder = reserve.subtract(actualFee);
        BigDecimal buyerRefund = buyerShare(order, reserve).subtract(buyerShare(order, actualFee));
        BigDecimal sellerRefund = remainder.subtract(buyerRefund);
        if (buyerRefund.signum() > 0) {
            walletService.processRefund(order.getBuyer().getId(), buyerRefund, order.getId(),
                    WalletTransactionReferenceType.INSPECTION_ORDER);
        }
        if (sellerRefund.signum() > 0) {
            walletService.processRefund(order.getSeller().getId(), sellerRefund, order.getId(),
                    WalletTransactionReferenceType.INSPECTION_ORDER);
        }
        settlement.setReturnReserveAllocated(actualFee);
        settlement.setBuyerRefundAmount(settlement.getBuyerRefundAmount().add(buyerRefund));
        settlement.setSellerRefundAmount(settlement.getSellerRefundAmount().add(sellerRefund));
        settlement.setReturnShippingSettledAt(Instant.now());
    }

    private BigDecimal buyerShare(InspectionOrder order, BigDecimal amount) {
        InspectionFeePayer payer = order.getFeePayer() == null ? InspectionFeePayer.BUYER_PAYS : order.getFeePayer();
        return payer.buyerShare(amount);
    }

    private BigDecimal computeCenterPayout(InspectionOrder order, BigDecimal serviceFee) {
        if (serviceFee.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal rate = order.getPlatformFeeRateSnapshot() == null ? platformFeeRate : order.getPlatformFeeRateSnapshot();
        BigDecimal commissionBase = order.getPlatformFeeRateSnapshot() == null ? serviceFee : order.getInspectionFee();
        BigDecimal platformCut = commissionBase.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        return serviceFee.subtract(platformCut).max(BigDecimal.ZERO);
    }

    private User resolveCenterRecipient(InspectionOrder order) {
        if (order.getInspectionCenter() != null) return order.getInspectionCenter();
        if (order.getInspector() != null) {
            User inspector = order.getInspector();
            if (inspector.getManager() != null) {
                return inspector.getManager();
            }
            return inspector;
        }
        List<UUID> centerManagerIds = userRepository.findActiveInspectionCenterUserIds();
        if (centerManagerIds != null && !centerManagerIds.isEmpty()) {
            return userRepository.findById(centerManagerIds.getFirst()).orElse(null);
        }
        return null;
    }
}

