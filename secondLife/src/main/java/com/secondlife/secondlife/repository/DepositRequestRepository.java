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
    interface PaymentReference { UUID getId(); UUID getOrderId(); }
    @org.springframework.data.jpa.repository.Query("select d.id as id,d.orderId as orderId from DepositRequest d where d.code=:code")
    Optional<PaymentReference> findReferenceByCode(@org.springframework.data.repository.query.Param("code") String code);
    Optional<DepositRequest> findByOrderId(UUID orderId);
    @org.springframework.data.jpa.repository.Query("select d.orderId from DepositRequest d, Order o where d.orderId=o.id and o.status=com.secondlife.secondlife.enums.OrderStatus.PENDING_PAYMENT and d.expiresAt<=:cutoff order by d.expiresAt,d.orderId")
    java.util.List<UUID> findExpiredOrderIds(@org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from DepositRequest d where d.id=:id")
    Optional<DepositRequest> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DepositRequest d WHERE d.code = :code")
    Optional<DepositRequest> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);
    boolean existsBySepayTransactionId(Long sepayTransactionId);
}
