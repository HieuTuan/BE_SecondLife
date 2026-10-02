package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CreditLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, UUID> {
    Page<CreditLedgerEntry> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
