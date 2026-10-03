package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.DepositRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepositRequestRepository extends JpaRepository<DepositRequest, UUID> {
    Optional<DepositRequest> findByCode(String code);
    boolean existsByCode(String code);
    boolean existsBySepayTransactionId(Long sepayTransactionId);
}
