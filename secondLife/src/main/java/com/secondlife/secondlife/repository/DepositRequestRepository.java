package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.DepositRequest;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepositRequestRepository extends JpaRepository<DepositRequest, UUID> {
    Optional<DepositRequest> findByCode(String code);
    interface PaymentReference { UUID getId(); UUID getOrderId(); }
    @Query("select d.id as id,d.orderId as orderId from DepositRequest d where d.code=:code")
    Optional<PaymentReference> findReferenceByCode(@Param("code") String code);
    Optional<DepositRequest> findByOrderId(UUID orderId);
    List<DepositRequest> findByOrderIdIn(Collection<UUID> orderIds);
    @Query("select d.orderId from DepositRequest d, Order o where d.orderId=o.id and o.status=com.secondlife.secondlife.enums.OrderStatus.PENDING_PAYMENT and d.expiresAt<=:cutoff order by d.expiresAt,d.orderId")
    List<UUID> findExpiredOrderIds(@Param("cutoff") Instant cutoff,Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DepositRequest d where d.id=:id")
    Optional<DepositRequest> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DepositRequest d WHERE d.code = :code")
    Optional<DepositRequest> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);
    boolean existsBySepayTransactionId(Long sepayTransactionId);
}
