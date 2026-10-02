package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CreditBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditBalanceRepository extends JpaRepository<CreditBalance, UUID> {
    List<CreditBalance> findByUserId(UUID userId);
}
