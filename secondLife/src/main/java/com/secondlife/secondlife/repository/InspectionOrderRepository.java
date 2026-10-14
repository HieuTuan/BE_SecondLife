package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionOrderRepository extends JpaRepository<InspectionOrder, UUID> {

    @Query("select o.post.user.id from InspectionOrder o where o.id = :id")
    Optional<UUID> findOwnerId(@Param("id") UUID id);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from InspectionOrder o where o.id = :id")
    Optional<InspectionOrder> findByIdForUpdate(@Param("id") UUID id);

    List<InspectionOrder> findByInspectorId(UUID inspectorId);

    List<InspectionOrder> findByInspectorIdOrderByCreatedAtDesc(UUID inspectorId);

    Page<InspectionOrder> findByStatus(String status, Pageable pageable);

    Page<InspectionOrder> findAll(Pageable pageable);

    Optional<InspectionOrder> findByPostId(UUID postId);

    Optional<InspectionOrder> findByPostIdAndStatus(UUID postId, String status);

    Optional<InspectionOrder> findByOrderId(UUID orderId);

    Optional<InspectionOrder> findByTamperSealId(String tamperSealId);

    Page<InspectionOrder> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId, Pageable pageable);

    Page<InspectionOrder> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);

    @Query("SELECT o FROM InspectionOrder o WHERE o.status = 'WAITING_FEE_PAYMENT' AND o.paymentDeadline IS NOT NULL AND o.paymentDeadline < :now")
    List<InspectionOrder> findExpiredUnpaidOrders(@Param("now") Instant now);

    @Query("SELECT o FROM InspectionOrder o WHERE o.status = 'PASSED' AND o.finalPaymentDeadline IS NOT NULL AND o.finalPaymentDeadline < :now AND o.finalPaymentPaidAt IS NULL")
    List<InspectionOrder> findExpiredFinalPaymentOrders(@Param("now") Instant now);

    @Query("SELECT o FROM InspectionOrder o WHERE o.videoCleanupStatus = 'ACTIVE' " +
           "AND o.videoRetentionUntil IS NOT NULL AND o.videoRetentionUntil <= :now " +
           "AND (o.videoProofUrl IS NOT NULL OR o.unboxingVideoUrl IS NOT NULL)")
    List<InspectionOrder> findDueForVideoCleanup(@Param("now") Instant now);
}
