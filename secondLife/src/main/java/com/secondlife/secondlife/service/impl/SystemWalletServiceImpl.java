package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.SystemWalletResponse;
import com.secondlife.secondlife.dto.response.SystemWalletTransactionResponse;
import com.secondlife.secondlife.entity.InspectionFeeSettlement;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.SystemOrderFunds;
import com.secondlife.secondlife.entity.SystemWallet;
import com.secondlife.secondlife.entity.SystemWalletStatementEntry;
import com.secondlife.secondlife.entity.SystemWalletTransaction;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.enums.SystemWalletFundCategory;
import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.SystemWalletMapper;
import com.secondlife.secondlife.repository.SystemOrderFundsRepository;
import com.secondlife.secondlife.repository.SystemWalletRepository;
import com.secondlife.secondlife.repository.SystemWalletStatementRepository;
import com.secondlife.secondlife.repository.SystemWalletTransactionRepository;
import com.secondlife.secondlife.service.SystemWalletService;
import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemWalletServiceImpl implements SystemWalletService {

    private final SystemWalletRepository wallets;
    private final SystemOrderFundsRepository funds;
    private final SystemWalletTransactionRepository transactions;
    private final SystemWalletStatementRepository statement;
    private final SystemWalletMapper systemWalletMapper;
    private final WalletService userWallets;

    // Callers lock the order first, then this wallet, then the user's wallet, in one transaction.
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void hold(Order order) {
        var wallet = lock();
        if (order.getEscrowStatus() != EscrowStatus.HELD || funds.existsById(order.getId()))
            throw new ConflictException("Order payment is already accounted for or escrow is not HELD");
        var product = money(order.getFinalPrice());
        var shipping = money(order.getShippingFee());
        if (product.signum() == 0) throw new ConflictException("Order price must be positive");
        var allocation = new SystemOrderFunds();
        allocation.setOrderId(order.getId()); allocation.setProductHeld(product); allocation.setShippingHeld(shipping);
        funds.save(allocation);
        record(wallet, order, SystemWalletTransactionType.PAYMENT_HOLD, product, shipping, BigDecimal.ZERO);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void release(Order order, BigDecimal platformCommission, BigDecimal sellerPayout) {
        var wallet = lock();
        var allocation = allocation(order);
        var commission = money(platformCommission);
        var payout = money(sellerPayout);
        if (order.getStatus() != OrderStatus.DELIVERED || order.getEscrowStatus() != EscrowStatus.HELD
                || order.getShippingDeliveredAt() == null
                || allocation.getProductHeld().compareTo(order.getFinalPrice()) != 0
                || commission.add(payout).compareTo(allocation.getProductHeld()) != 0)
            throw new ConflictException("Settlement does not match the order's held funds");
        BigDecimal product = allocation.getProductHeld();
        allocation.setProductHeld(BigDecimal.ZERO); funds.save(allocation);
        record(wallet, order, SystemWalletTransactionType.SETTLEMENT, product.negate(), BigDecimal.ZERO, commission);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void refund(Order order, BigDecimal amount) {
        var wallet = lock();
        var allocation = allocation(order);
        var refund = money(amount);
        var product = allocation.getProductHeld();
        var shipping = refund.subtract(product);
        if ((order.getEscrowStatus() != EscrowStatus.HELD && order.getEscrowStatus() != EscrowStatus.FROZEN)
                || product.signum() <= 0 || product.compareTo(order.getFinalPrice()) != 0
                || shipping.signum() < 0 || shipping.compareTo(allocation.getShippingHeld()) > 0)
            throw new ConflictException("Refund exceeds the order's held funds");
        allocation.setProductHeld(BigDecimal.ZERO);
        allocation.setShippingHeld(allocation.getShippingHeld().subtract(shipping)); funds.save(allocation);
        record(wallet, order, SystemWalletTransactionType.REFUND, product.negate(), shipping.negate(), BigDecimal.ZERO);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal collectReturnShippingFee(Order order, BigDecimal fee, UUID shipmentId) {
        if (order.getStatus() != OrderStatus.CANCELLED || order.getEscrowStatus() != EscrowStatus.REFUNDED)
            throw new ConflictException("Return shipping collection requires a refunded order");
        var amount = money(fee);
        // Acquire MAIN before touching the seller wallet, including when assessing a pending fee on retry.
        var wallet = lock();
        BigDecimal collected = userWallets.collectReturnShippingFee(order.getSeller().getId(), amount, shipmentId);
        if (collected.signum() > 0)
            record(wallet, order, SystemWalletTransactionType.RETURN_SHIPPING_FEE,
                    BigDecimal.ZERO, collected, BigDecimal.ZERO);
        return collected;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void holdInspectionFee(InspectionOrder order, boolean buyer, BigDecimal feeShare, BigDecimal deposit) {
        if (order.isCashShippingSimulation() || feeShare.add(deposit).signum() == 0) {
            return;
        }
        UUID inspectionId = order.getId();
        boolean otherPartyPaid = buyer
                ? order.getSellerPaidAt() != null && inspectionAmount(order.getSellerFeeShare()).signum() > 0
                : order.getBuyerPaidAt() != null && inspectionAmount(order.getBuyerFeeShare())
                        .add(inspectionAmount(order.getBuyerCommitmentDeposit())).signum() > 0;
        // Payments made before the ledger was introduced cannot be split safely from new deposits.
        if (otherPartyPaid && !transactions.existsByInspectionOrderId(inspectionId)) {
            return;
        }

        BigDecimal shippingTotal = inspectionAmount(order.getShippingFee())
                .add(inspectionAmount(order.getReturnShippingReserveFee()));
        BigDecimal buyerShipping = buyerShippingShare(order, shippingTotal);
        BigDecimal shipping = (buyer ? buyerShipping : shippingTotal.subtract(buyerShipping)).min(feeShare);
        BigDecimal held = feeShare.add(deposit).subtract(shipping);
        recordInspection(lock(), inspectionId, buyer ? InspectionEntry.BUYER_PAYMENT : InspectionEntry.SELLER_PAYMENT,
                held, shipping, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean lockInspectionFunds(UUID inspectionOrderId) {
        if (!transactions.existsByInspectionOrderId(inspectionOrderId)) {
            return false;
        }
        lock();
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleInspectionFees(InspectionFeeSettlement settlement) {
        InspectionOrder order = settlement.getInspectionOrder();
        UUID inspectionId = order.getId();
        var wallet = lock();
        var balances = transactions.getInspectionBalances(inspectionId);
        BigDecimal refunds = settlement.getBuyerRefundAmount().add(settlement.getSellerRefundAmount());
        BigDecimal shippingRefund = switch (settlement.getOutcome()) {
            case PAYMENT_EXPIRED, CANCELLED_BEFORE_INSPECTION -> balances.getShipping();
            case PASSED_PURCHASED -> inspectionAmount(order.getReturnShippingReserveFee());
            case FAILED_INSPECTION, PASSED_BUYER_DEFAULTED -> BigDecimal.ZERO;
        };
        BigDecimal heldRefund = refunds.subtract(shippingRefund);
        BigDecimal payout = settlement.getCenterPayoutAmount().add(settlement.getSellerCompensationAmount());
        BigDecimal commission = settlement.getPlatformRetainedAmount();
        BigDecimal supplemental = settlement.getSellerAdditionalCollected();
        BigDecimal heldSupplement = heldRefund.add(payout).add(commission).subtract(balances.getHeld())
                .max(BigDecimal.ZERO).min(supplemental);
        BigDecimal shippingSupplement = supplemental.subtract(heldSupplement);
        BigDecimal heldAfterRefund = balances.getHeld().add(heldSupplement).subtract(heldRefund);
        BigDecimal shippingAfterRefund = balances.getShipping().add(shippingSupplement).subtract(shippingRefund);
        if (heldRefund.signum() < 0 || heldAfterRefund.signum() < 0 || shippingAfterRefund.signum() < 0) {
            throw new ConflictException("Inspection refund exceeds its funded balance");
        }

        BigDecimal fundedPayout = payout.min(heldAfterRefund);
        BigDecimal fundedCommission = commission.min(heldAfterRefund.subtract(fundedPayout));
        BigDecimal outbound = settlement.getOutboundShippingAllocated();
        BigDecimal fundedShipping = outbound.min(shippingAfterRefund);
        if (heldAfterRefund.compareTo(fundedPayout.add(fundedCommission)) != 0
                || shippingAfterRefund.subtract(fundedShipping).compareTo(settlement.getReturnReserveAllocated()) > 0) {
            throw new ConflictException("Inspection settlement does not reconcile with its funded balance");
        }

        recordInspection(wallet, inspectionId, InspectionEntry.SELLER_SETTLEMENT_PAYMENT, heldSupplement,
                shippingSupplement, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        recordInspection(wallet, inspectionId, InspectionEntry.FEE_REFUND, heldRefund.negate(),
                shippingRefund.negate(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        recordInspection(wallet, inspectionId, InspectionEntry.FEE_SETTLEMENT, fundedPayout.add(fundedCommission).negate(),
                fundedShipping.negate(), fundedCommission, outbound,
                payout.subtract(fundedPayout).add(outbound.subtract(fundedShipping)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void settleInspectionReturn(UUID inspectionId, BigDecimal carrierFee, BigDecimal sellerCollected,
                                       BigDecimal reserveRefund) {
        var wallet = lock();
        var balances = transactions.getInspectionBalances(inspectionId);
        BigDecimal availableShipping = balances.getShipping().add(sellerCollected);
        BigDecimal fundedShipping = carrierFee.min(availableShipping);
        if (balances.getHeld().signum() != 0 || availableShipping.subtract(fundedShipping).compareTo(reserveRefund) != 0) {
            throw new ConflictException("Inspection return does not reconcile with its funded reserve");
        }

        recordInspection(wallet, inspectionId, InspectionEntry.SELLER_RETURN_PAYMENT, BigDecimal.ZERO, sellerCollected,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        recordInspection(wallet, inspectionId, InspectionEntry.RETURN_SHIPPING, BigDecimal.ZERO, fundedShipping.negate(),
                BigDecimal.ZERO, carrierFee, carrierFee.subtract(fundedShipping));
        recordInspection(wallet, inspectionId, InspectionEntry.RETURN_REFUND, BigDecimal.ZERO, reserveRefund.negate(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal buyerShippingShare(InspectionOrder order, BigDecimal shipping) {
        return order.buyerShareOf(shipping).min(inspectionAmount(order.getBuyerFeeShare()));
    }

    private BigDecimal inspectionAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : money(amount);
    }

    private void recordInspection(SystemWallet wallet, UUID inspectionId, InspectionEntry entry,
                                  BigDecimal held, BigDecimal shipping, BigDecimal revenue,
                                  BigDecimal carrierFee, BigDecimal unfundedExpense) {
        if (held.signum() == 0 && shipping.signum() == 0 && revenue.signum() == 0
                && carrierFee.signum() == 0 && unfundedExpense.signum() == 0) {
            return;
        }
        var event = new SystemWalletTransaction();
        event.setInspectionOrderId(inspectionId);
        event.setInspectionEntry(entry.name());
        event.setType(entry.type);
        event.setCarrierFee(carrierFee);
        event.setUnfundedExpense(unfundedExpense);
        record(wallet, event, held, shipping, revenue);
    }

    private enum InspectionEntry {
        BUYER_PAYMENT(SystemWalletTransactionType.INSPECTION_HOLD),
        SELLER_PAYMENT(SystemWalletTransactionType.INSPECTION_HOLD),
        SELLER_SETTLEMENT_PAYMENT(SystemWalletTransactionType.INSPECTION_HOLD),
        SELLER_RETURN_PAYMENT(SystemWalletTransactionType.INSPECTION_HOLD),
        FEE_SETTLEMENT(SystemWalletTransactionType.INSPECTION_SETTLEMENT),
        FEE_REFUND(SystemWalletTransactionType.INSPECTION_REFUND),
        RETURN_SHIPPING(SystemWalletTransactionType.INSPECTION_RETURN_SHIPPING),
        RETURN_REFUND(SystemWalletTransactionType.INSPECTION_REFUND);

        private final SystemWalletTransactionType type;

        InspectionEntry(SystemWalletTransactionType type) {
            this.type = type;
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SystemWalletResponse getWallet() {
        var wallet = wallets.findById(SystemWallet.MAIN)
                .orElseThrow(() -> new ConflictException("System wallet is not initialized"));
        return systemWalletMapper.toResponse(wallet, transactions.getInspectionHeldBalance(), transactions.getTotalHubPaid());
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<SystemWalletTransactionResponse> getTransactions(
            SystemWalletTransactionType type, Boolean hasShipping, UUID orderId,
            SystemWalletFundCategory fundCategory, Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        if (sort.getOrderFor("lineNumber") == null) {
            sort = sort.and(Sort.by("lineNumber"));
        }
        Pageable sortedPage = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        var page = statement.search(type, hasShipping, orderId, fundCategory, sortedPage);
        if (page.isEmpty()) {
            return page.map(entry -> systemWalletMapper.toTransactionResponse(entry, BigDecimal.ZERO));
        }
        var orderIds = page.stream().map(SystemWalletStatementEntry::getOrderId).collect(Collectors.toSet());
        var shippingPaid = transactions.findShippingPayments(orderIds).stream().collect(Collectors.toMap(
                payment -> new OrderReference(payment.getOrderId(), payment.getInspectionOrderId()),
                SystemWalletTransactionRepository.ShippingPayment::getShippingFee));
        return page.map(entry -> systemWalletMapper.toTransactionResponse(entry,
                shippingPaid.getOrDefault(new OrderReference(entry.getOrderId(), entry.getInspectionOrderId()), BigDecimal.ZERO)));
    }

    private record OrderReference(UUID orderId, UUID inspectionOrderId) {
    }

    private SystemWallet lock() {
        return wallets.findByIdForUpdate(SystemWallet.MAIN).orElseThrow(() -> new ConflictException("System wallet is not initialized"));
    }

    private SystemOrderFunds allocation(Order order) {
        return funds.findById(order.getId()).orElseThrow(() -> new ConflictException("Order has no accounted escrow funds"));
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new ConflictException("Invalid order funds");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void record(SystemWallet wallet, Order order, SystemWalletTransactionType type,
                        BigDecimal held, BigDecimal shipping, BigDecimal revenue) {
        var event = new SystemWalletTransaction();
        event.setOrderId(order.getId());
        event.setType(type);
        record(wallet, event, held, shipping, revenue);
    }

    private void record(SystemWallet wallet, SystemWalletTransaction event,
                        BigDecimal held, BigDecimal shipping, BigDecimal revenue) {
        wallet.setHeldBalance(wallet.getHeldBalance().add(held));
        wallet.setShippingBalance(wallet.getShippingBalance().add(shipping));
        wallet.setRevenueBalance(wallet.getRevenueBalance().add(revenue));
        if (wallet.getHeldBalance().signum() < 0 || wallet.getShippingBalance().signum() < 0 || wallet.getRevenueBalance().signum() < 0)
            throw new ConflictException("System wallet balances are inconsistent with order funds");
        wallet.setUpdatedAt(Instant.now()); wallets.save(wallet);
        event.setHeldDelta(held); event.setShippingDelta(shipping); event.setRevenueDelta(revenue);
        event.setHeldBalanceAfter(wallet.getHeldBalance()); event.setShippingBalanceAfter(wallet.getShippingBalance());
        event.setRevenueBalanceAfter(wallet.getRevenueBalance()); event.setCreatedAt(wallet.getUpdatedAt());
        transactions.save(event);
    }
}
