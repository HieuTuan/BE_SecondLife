package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.config.JpaRepositoryConfiguration.RefreshOperations;
import com.secondlife.secondlife.entity.Shipment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ShipmentRepository extends JpaRepository<Shipment,UUID>, RefreshOperations {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from Shipment s where s.id=:id")
    Optional<Shipment> findByIdForUpdate(@Param("id") UUID id);
    List<Shipment> findByOrderIdOrderByCreatedAtAsc(UUID id);
    List<Shipment> findByInspectionOrderIdOrderByCreatedAtAsc(UUID id);
    Optional<Shipment> findByOrderCode(String code);
    Optional<Shipment> findByProviderAndOrderCode(String provider, String code);
    Optional<Shipment> findByClientOrderCode(String code);
    boolean existsByOrderId(UUID id);

    @Query(value = "SELECT EXISTS(SELECT 1 FROM shipments WHERE order_id=:orderId AND leg='BUYER_TO_SELLER')", nativeQuery = true)
    boolean hasReturnShipment(@Param("orderId") UUID orderId);

}
