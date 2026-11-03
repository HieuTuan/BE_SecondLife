package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.TopupPackagePurchase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TopupPackagePurchaseRepository extends JpaRepository<TopupPackagePurchase, UUID> {
    Optional<TopupPackagePurchase> findByUserIdAndRequestId(UUID userId, UUID requestId);
    Optional<TopupPackagePurchase> findByIdAndUserId(UUID id, UUID userId);
    Page<TopupPackagePurchase> findByUserId(UUID userId, Pageable pageable);
}
