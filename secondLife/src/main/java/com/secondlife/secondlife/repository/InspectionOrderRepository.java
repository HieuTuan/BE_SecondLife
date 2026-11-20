package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionOrder;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionOrderRepository extends JpaRepository<InspectionOrder, UUID> {

    Optional<InspectionOrder> findByInspectionShippingQuoteId(UUID quoteId);

    @Query("select o.post.user.id from InspectionOrder o where o.id = :id")
    Optional<UUID> findOwnerId(@Param("id") UUID id);

    @Query("select o.post.id from InspectionOrder o where o.id = :id")
    Optional<UUID> findPostId(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from InspectionOrder o where o.id = :id or (o.order is not null and o.order.id = :id)")
    Optional<InspectionOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select distinct i from InspectionOrder i
            join fetch i.post p left join fetch p.images
            left join fetch i.buyer b left join fetch b.profile
            left join fetch i.seller s left join fetch s.profile
            left join fetch i.inspector t left join fetch t.profile
            where i.id in :ids
            """)
    List<InspectionOrder> findFeedInspectionsByIds(@Param("ids") List<UUID> ids);

    List<InspectionOrder> findByInspectorId(UUID inspectorId);

    @EntityGraph(attributePaths = {"post", "buyer.profile", "seller.profile", "inspector.profile"})
    List<InspectionOrder> findByInspectorIdOrderByCreatedAtDesc(UUID inspectorId);

    @EntityGraph(attributePaths = {"post", "buyer.profile", "seller.profile", "inspector.profile"})
    Page<InspectionOrder> findByStatus(String status, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "buyer.profile", "seller.profile", "inspector.profile"})
    Page<InspectionOrder> findByStatusIn(Collection<String> statuses, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "buyer.profile", "seller.profile", "inspector.profile"})
    Page<InspectionOrder> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"post", "post.user", "buyer.profile", "seller.profile", "inspector.profile", "order", "inspectionCenter.profile"})
    @Query("""
            select o from InspectionOrder o left join o.inspector i left join i.manager m
            where (o.inspectionCenter.id=:centerId or (o.inspectionCenter is null and m.id=:centerId))
              and (:status is null or o.status=:status)
            """)
    Page<InspectionOrder> findVisibleToCenter(@Param("centerId") UUID centerId, @Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "post.user", "buyer.profile", "seller.profile", "inspector.profile", "order", "inspectionCenter.profile"})
    @Query("select o from InspectionOrder o where o.inspector.id=:inspectorId and (:status is null or o.status=:status)")
    Page<InspectionOrder> findVisibleToInspector(@Param("inspectorId") UUID inspectorId, @Param("status") String status, Pageable pageable);

    @Query(value = """
            SELECT DISTINCT ON (o.post_id) o.post_id AS "postId",
                   o.tamper_seal_id AS "tamperSealId", o.condition_grade AS "conditionGrade"
            FROM inspection_orders o
            WHERE o.post_id IN (:postIds)
              AND (o.tamper_seal_id IS NOT NULL OR o.status = 'PASSED'
                OR (o.status = 'CANCELLED' AND EXISTS (
                    SELECT 1 FROM inspection_fee_settlements s
                    WHERE s.inspection_order_id = o.id
                      AND s.outcome = 'PASSED_BUYER_DEFAULTED'
                      AND s.return_shipping_settled_at IS NOT NULL
                )))
            ORDER BY o.post_id, o.created_at DESC NULLS LAST, o.id DESC
            """, nativeQuery = true)
    List<ListingInspection> findLatestListingInspections(@Param("postIds") Collection<UUID> postIds);

    interface ListingInspection {
        UUID getPostId();

        String getTamperSealId();

        String getConditionGrade();
    }

    List<InspectionOrder> findAllByPostIdOrderByCreatedAtDesc(UUID postId);

    Optional<InspectionOrder> findFirstByPostIdOrderByCreatedAtDesc(UUID postId);

    Optional<InspectionOrder> findFirstByPostIdAndStatusOrderByCreatedAtDesc(UUID postId, String status);

    default Optional<InspectionOrder> findByPostId(UUID postId) {
        return findFirstByPostIdOrderByCreatedAtDesc(postId);
    }

    Optional<InspectionOrder> findByPostIdAndStatus(UUID postId, String status);

    @Query("SELECT o FROM InspectionOrder o WHERE o.post.id = :postId AND o.status NOT IN ('CANCELLED', 'FAILED', 'PAYMENT_EXPIRED', 'COMPLETED') ORDER BY o.createdAt DESC")
    Optional<InspectionOrder> findActiveByPostId(@Param("postId") UUID postId);

    Optional<InspectionOrder> findByOrderId(UUID orderId);

    Optional<InspectionOrder> findByTamperSealId(String tamperSealId);

    @EntityGraph(attributePaths = {"post", "buyer.profile", "seller.profile", "inspector.profile"})
    Page<InspectionOrder> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId, Pageable pageable);

    @EntityGraph(attributePaths = {"post", "buyer.profile", "seller.profile", "inspector.profile"})
    Page<InspectionOrder> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);

    @Query("SELECT o FROM InspectionOrder o WHERE o.status IN ('WAITING_SELLER_APPROVAL', 'WAITING_BUYER_APPROVAL') AND o.approvalDeadline IS NOT NULL AND o.approvalDeadline < :now")
    List<InspectionOrder> findExpiredApprovalOrders(@Param("now") Instant now);

    @Query("SELECT o FROM InspectionOrder o WHERE o.status = 'WAITING_FEE_PAYMENT' AND o.paymentDeadline IS NOT NULL AND o.paymentDeadline < :now")
    List<InspectionOrder> findExpiredUnpaidOrders(@Param("now") Instant now);

    @Query("SELECT o.id FROM InspectionOrder o WHERE o.status = 'PASSED' AND o.finalPaymentDeadline IS NOT NULL AND o.finalPaymentDeadline < :now AND o.finalPaymentPaidAt IS NULL")
    List<UUID> findExpiredFinalPaymentOrderIds(@Param("now") Instant now);

    @Query("SELECT o FROM InspectionOrder o WHERE o.status = 'FAILED' AND o.appealStatus = com.secondlife.secondlife.enums.InspectionAppealStatus.NONE AND o.reputationPenaltyApplied = false AND o.appealDeadline IS NOT NULL AND o.appealDeadline < :now")
    List<InspectionOrder> findExpiredAppeals(@Param("now") Instant now);

    @Query("SELECT o FROM InspectionOrder o WHERE o.videoCleanupStatus = 'ACTIVE' " +
           "AND o.videoRetentionUntil IS NOT NULL AND o.videoRetentionUntil <= :now " +
           "AND (o.videoProofUrl IS NOT NULL OR o.unboxingVideoUrl IS NOT NULL)")
    List<InspectionOrder> findDueForVideoCleanup(@Param("now") Instant now);
}
