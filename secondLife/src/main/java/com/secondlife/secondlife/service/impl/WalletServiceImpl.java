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
import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final UserWalletRepository userWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;

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
}
