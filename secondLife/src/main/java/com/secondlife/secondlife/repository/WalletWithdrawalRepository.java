package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.WalletWithdrawal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletWithdrawalRepository extends JpaRepository<WalletWithdrawal, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletWithdrawal w WHERE w.id = :id")
    Optional<WalletWithdrawal> findByIdForUpdate(@Param("id") UUID id);

    Page<WalletWithdrawal> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<WalletWithdrawal> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<WalletWithdrawal> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
