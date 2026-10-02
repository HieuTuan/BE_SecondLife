package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CreditPurchase;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CreditPurchaseRepository extends JpaRepository<CreditPurchase, UUID> {
    Page<CreditPurchase> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CreditPurchase p where p.id = :id")
    Optional<CreditPurchase> findByIdForUpdate(@Param("id") UUID id);
}
