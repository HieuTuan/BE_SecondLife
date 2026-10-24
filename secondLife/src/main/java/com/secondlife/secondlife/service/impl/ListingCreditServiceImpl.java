package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.service.ListingCreditService;

import com.secondlife.secondlife.entity.CreditLedgerEntry;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.repository.CreditBalanceRepository;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service("listingCreditService")
@RequiredArgsConstructor
public class ListingCreditServiceImpl implements ListingCreditService {
    private final CreditBalanceRepository balances;
    private final CreditLedgerRepository ledger;

    /** Callers lock seller/post first; debit and domain result commit or roll back together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(UUID userId, CreditType type, String key) {
        if (ledger.existsByIdempotencyKey(key)) return;
        long after = balances.consumeOne(userId, type);
        CreditLedgerEntry entry = new CreditLedgerEntry();
        entry.setUserId(userId); entry.setCreditType(type); entry.setEntryType("CONSUME");
        entry.setQuantityDelta(-1); entry.setBalanceAfter(after); entry.setIdempotencyKey(key);
        entry.setCreatedAt(Instant.now()); ledger.save(entry);
    }

    /**
     * Autonomous transaction: commits credit deduction immediately.
     * Used when external AI resources (Vision/Scan) are consumed, so credits remain consumed
     * even if downstream validation throws an exception.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumeImmediately(UUID userId, CreditType type, String key) {
        if (ledger.existsByIdempotencyKey(key)) return;
        long after = balances.consumeOne(userId, type);
        CreditLedgerEntry entry = new CreditLedgerEntry();
        entry.setUserId(userId); entry.setCreditType(type); entry.setEntryType("CONSUME");
        entry.setQuantityDelta(-1); entry.setBalanceAfter(after); entry.setIdempotencyKey(key);
        entry.setCreatedAt(Instant.now()); ledger.save(entry);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void refund(UUID userId, CreditType type, String key) {
        if (ledger.existsByIdempotencyKey(key)) return;
        long after = balances.grant(userId, type, 1);
        CreditLedgerEntry entry = new CreditLedgerEntry();
        entry.setUserId(userId); entry.setCreditType(type); entry.setEntryType("REFUND");
        entry.setQuantityDelta(1); entry.setBalanceAfter(after); entry.setIdempotencyKey(key);
        entry.setCreatedAt(Instant.now()); ledger.save(entry);
    }
}
