package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.CreditLedgerEntry;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.repository.CreditBalanceGrantRepository;
import com.secondlife.secondlife.repository.CreditLedgerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingCreditService {
    private final CreditBalanceGrantRepository balances;
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
