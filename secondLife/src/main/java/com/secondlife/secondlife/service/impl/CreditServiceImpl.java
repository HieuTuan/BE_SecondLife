package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.TopupPackage;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.repository.TopupPackageRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.repository.CreditBalanceGrantRepository;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import com.secondlife.secondlife.entity.CreditLedgerEntry;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.dto.credit.CreditBalanceResponse;
import com.secondlife.secondlife.service.CreditService;
import com.secondlife.secondlife.service.CreditBalanceService;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CreditServiceImpl implements CreditService {
    private final TopupPackageRepository topupPackageRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final CreditBalanceGrantRepository balanceGrantRepository;
    private final CreditLedgerRepository ledgerRepository;
    private final CreditBalanceService creditBalanceService;

    public CreditServiceImpl(TopupPackageRepository topupPackageRepository,
                             UserRepository userRepository,
                             WalletService walletService,
                             CreditBalanceGrantRepository balanceGrantRepository,
                             CreditLedgerRepository ledgerRepository,
                             CreditBalanceService creditBalanceService) {
        this.topupPackageRepository = topupPackageRepository;
        this.userRepository = userRepository;
        this.walletService = walletService;
        this.balanceGrantRepository = balanceGrantRepository;
        this.ledgerRepository = ledgerRepository;
        this.creditBalanceService = creditBalanceService;
    }

    @Override
    @Transactional(readOnly = true)
    public CreditBalanceResponse getUserCredit(UUID userId) {
        // Create user check
        userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return creditBalanceService.getBalance(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopupPackage> getAllTopupPackages() {
        return topupPackageRepository.findAll();
    }

    @Override
    @Transactional
    public CreditBalanceResponse purchaseTopupPackage(UUID userId, UUID packageId) {
        TopupPackage topupPackage = topupPackageRepository.findById(packageId)
                .orElseThrow(() -> new NotFoundException("Topup package not found"));

        // Deduct from wallet (throws exception if insufficient balance)
        walletService.processPayment(userId, topupPackage.getPrice(), packageId);

        // Add credits to user via grant repository
        grant(userId, CreditType.LISTING, topupPackage.getPostCredits(), packageId);
        grant(userId, CreditType.AI_CHAT, topupPackage.getChatCredits(), packageId);
        grant(userId, CreditType.VALUATION, topupPackage.getValuationCredits(), packageId);
        
        return creditBalanceService.getBalance(userId);
    }
    
    private void grant(UUID userId, CreditType type, int quantity, UUID packageId) {
        if (quantity == 0) return;
        long balanceAfter = balanceGrantRepository.grant(userId, type, quantity);
        CreditLedgerEntry entry = new CreditLedgerEntry();
        entry.setUserId(userId);
        entry.setCreditType(type);
        entry.setEntryType("GRANT");
        entry.setQuantityDelta(quantity);
        entry.setBalanceAfter(balanceAfter);
        entry.setIdempotencyKey("TOPUP_GRANT:" + packageId + ":" + userId + ":" + type.name());
        entry.setCreatedAt(java.time.Instant.now());
        ledgerRepository.save(entry);
    }
}
