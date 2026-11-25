package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.config.JpaRepositoryConfiguration.RefreshOperations;
import com.secondlife.secondlife.entity.Shipment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import java.util.*;
public interface ShipmentRepository extends JpaRepository<Shipment,UUID>, RefreshOperations {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from Shipment s where s.id=:id")
    Optional<Shipment> findByIdForUpdate(@Param("id") UUID id);
    List<Shipment> findByOrderIdOrderByCreatedAtAsc(UUID id);
    List<Shipment> findByInspectionOrderIdOrderByCreatedAtAsc(UUID id);
    @Query("""
            select s from Shipment s
            where s.inspectionOrderId = :inspectionId
               or (s.inspectionOrderId is null and s.orderId = :orderId and s.leg = 'CENTER_TO_BUYER')
            order by s.createdAt, s.id
            """)
    List<Shipment> findInspectionRoute(@Param("inspectionId") UUID inspectionId, @Param("orderId") UUID orderId);

    boolean existsByInspectionOrderIdAndLegAndCashConfirmedAtIsNotNull(UUID inspectionOrderId, String leg);

    Optional<Shipment> findByOrderCode(String code);
    Optional<Shipment> findByProviderAndOrderCode(String provider, String code);
    Optional<Shipment> findByClientOrderCode(String code);
    boolean existsByOrderId(UUID id);

    @Query(value = """
            SELECT s.id FROM shipments s
            JOIN user_reports r ON r.id = s.report_id AND r.return_shipment_id = s.id AND r.order_id = s.order_id
            JOIN orders o ON o.id = s.order_id
            WHERE s.leg = 'BUYER_TO_SELLER' AND s.status = 'DELIVERED' AND s.delivered_at IS NOT NULL
              AND r.status = 'CONFIRMED' AND r.resolution_action = 'RETURN_AND_REFUND'
              AND r.return_approved_at IS NOT NULL AND r.refunded_at IS NULL
              AND o.status = 'DELIVERED' AND o.escrow_status = 'FROZEN'
              AND (CAST(:afterId AS uuid) IS NULL OR s.id > :afterId)
            ORDER BY s.id
            """, nativeQuery = true)
    List<UUID> findDeliveredDisputeReturns(@Param("afterId") UUID afterId, Pageable pageable);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM shipments WHERE order_id=:orderId AND leg='BUYER_TO_SELLER')", nativeQuery = true)
    boolean hasReturnShipment(@Param("orderId") UUID orderId);

}
