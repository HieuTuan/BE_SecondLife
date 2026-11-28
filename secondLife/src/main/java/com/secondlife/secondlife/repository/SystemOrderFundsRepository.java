package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SystemOrderFunds;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.UUID;

public interface SystemOrderFundsRepository extends JpaRepository<SystemOrderFunds, UUID> {
    @Query("SELECT COALESCE(SUM(f.productHeld), 0) FROM SystemOrderFunds f WHERE f.productHeld > 0")
    BigDecimal getTotalProductHeld();

    Page<SystemOrderFunds> findByProductHeldGreaterThan(BigDecimal amount, Pageable pageable);
}
