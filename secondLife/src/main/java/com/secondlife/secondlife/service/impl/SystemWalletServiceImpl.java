package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.SystemWalletResponse;
import com.secondlife.secondlife.dto.response.SystemWalletTransactionResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.SystemOrderFunds;
import com.secondlife.secondlife.entity.SystemWallet;
import com.secondlife.secondlife.entity.SystemWalletTransaction;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.SystemWalletMapper;
import com.secondlife.secondlife.repository.SystemOrderFundsRepository;
import com.secondlife.secondlife.repository.SystemWalletRepository;
import com.secondlife.secondlife.repository.SystemWalletTransactionRepository;
import com.secondlife.secondlife.service.SystemWalletService;
import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SystemWalletServiceImpl implements SystemWalletService {

    private final SystemWalletRepository wallets;
    private final SystemOrderFundsRepository funds;
    private final SystemWalletTransactionRepository transactions;
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

    @Override @Transactional(readOnly = true)
    public SystemWalletResponse getWallet() {
        return systemWalletMapper.toResponse(wallets.findById(SystemWallet.MAIN).orElseThrow(() ->
                new ConflictException("System wallet is not initialized")));
    }

    @Override @Transactional(readOnly = true)
    public Page<SystemWalletTransactionResponse> getTransactions(Pageable pageable) {
        return transactions.findAll(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "id"))).map(systemWalletMapper::toTransactionResponse);
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
        wallet.setHeldBalance(wallet.getHeldBalance().add(held));
        wallet.setShippingBalance(wallet.getShippingBalance().add(shipping));
        wallet.setRevenueBalance(wallet.getRevenueBalance().add(revenue));
        if (wallet.getHeldBalance().signum() < 0 || wallet.getShippingBalance().signum() < 0 || wallet.getRevenueBalance().signum() < 0)
            throw new ConflictException("System wallet balances are inconsistent with order funds");
        wallet.setUpdatedAt(Instant.now()); wallets.save(wallet);
        var event = new SystemWalletTransaction();
        event.setOrderId(order.getId()); event.setType(type);
        event.setHeldDelta(held); event.setShippingDelta(shipping); event.setRevenueDelta(revenue);
        event.setHeldBalanceAfter(wallet.getHeldBalance()); event.setShippingBalanceAfter(wallet.getShippingBalance());
        event.setRevenueBalanceAfter(wallet.getRevenueBalance()); event.setCreatedAt(wallet.getUpdatedAt());
        transactions.save(event);
    }
}
