package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserWallet;
import com.secondlife.secondlife.entity.WalletTransaction;
import com.secondlife.secondlife.enums.WalletTransactionType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.UserWalletRepository;
import com.secondlife.secondlife.repository.WalletTransactionRepository;
import com.secondlife.secondlife.notification.NotificationType;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private final UserWalletRepository userWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository) {
        this(userWalletRepository, walletTransactionRepository, userRepository, null);
    }

    @Autowired
    public WalletServiceImpl(UserWalletRepository userWalletRepository,
                             WalletTransactionRepository walletTransactionRepository,
                             UserRepository userRepository,
                             @Autowired(required = false) NotificationService notificationService) {
        this.userWalletRepository = userWalletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    public UserWallet getWalletByUserId(UUID userId) {
        return userWalletRepository.findByUserId(userId)
                .orElseGet(() -> createWalletForUser(userId));
    }

    @Override
    @Transactional
    public UserWallet createWalletForUser(UUID userId) {
        if (userWalletRepository.findByUserId(userId).isPresent()) {
            return userWalletRepository.findByUserId(userId).get();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserWallet wallet = new UserWallet();
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);

        return userWalletRepository.save(wallet);
    }

    @Override
    @Transactional
    public void deposit(UUID userId, BigDecimal amount, UUID referenceId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Deposit amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        userWalletRepository.save(wallet);

        createTransaction(wallet, amount, WalletTransactionType.DEPOSIT, referenceId);

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                userId,
                "Nạp tiền vào ví thành công: +" + amount + " VND",
                "Bạn đã nạp thành công " + amount + " VND vào ví SecondLife.",
                NotificationType.WALLET,
                referenceId,
                "WALLET_TRANSACTION",
                "/wallet"
            );
        }
    }

    @Override
    @Transactional
    public void processPayment(UUID userId, BigDecimal amount, UUID orderId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));
        userWalletRepository.save(wallet);

        // Record a negative transaction for payment
        createTransaction(wallet, amount.negate(), WalletTransactionType.PAYMENT, orderId);
    }

    @Override
    @Transactional
    public void processEarning(UUID userId, BigDecimal amount, UUID orderId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Earning amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        userWalletRepository.save(wallet);

        createTransaction(wallet, amount, WalletTransactionType.EARNING, orderId);

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                userId,
                "Giải ngân tiền bán hàng: +" + amount + " VND",
                "Bạn đã nhận được " + amount + " VND doanh thu bán hàng vào ví SecondLife.",
                NotificationType.WALLET,
                orderId,
                "ORDER",
                "/wallet"
            );
        }
    }

    @Override
    @Transactional
    public void processRefund(UUID userId, BigDecimal amount, UUID orderId) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Refund amount must be greater than 0");
        }

        UserWallet wallet = getWalletWithLock(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        userWalletRepository.save(wallet);

        createTransaction(wallet, amount, WalletTransactionType.REFUND, orderId);

        if (notificationService != null) {
            notificationService.notifyWithEmail(
                userId,
                "Hoàn tiền vào ví: +" + amount + " VND",
                "Bạn đã nhận được khoản hoàn tiền " + amount + " VND vào ví SecondLife cho đơn hàng.",
                NotificationType.WALLET,
                orderId,
                "ORDER",
                "/wallet"
            );
        }
    }

    private UserWallet getWalletWithLock(UUID userId) {
        return userWalletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    // Create if not exists, then lock
                    createWalletForUser(userId);
                    return userWalletRepository.findByUserIdWithLock(userId).orElseThrow();
                });
    }

    private void createTransaction(UserWallet wallet, BigDecimal amount, WalletTransactionType type, UUID referenceId) {
        WalletTransaction transaction = new WalletTransaction();
        transaction.setWallet(wallet);
        transaction.setAmount(amount);
        transaction.setType(type);
        transaction.setReferenceId(referenceId);
        walletTransactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<WalletTransaction> getTransactions(UUID userId, org.springframework.data.domain.Pageable pageable) {
        UserWallet wallet = getWalletByUserId(userId);
        return walletTransactionRepository.findByWalletId(wallet.getId(), pageable);
    }
}
