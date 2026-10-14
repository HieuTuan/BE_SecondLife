package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.DepositRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

@Repository
public interface DepositRequestRepository extends JpaRepository<DepositRequest, UUID> {
    Optional<DepositRequest> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DepositRequest d WHERE d.code = :code")
    Optional<DepositRequest> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);
    boolean existsBySepayTransactionId(Long sepayTransactionId);
}
