package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SystemWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SystemWalletTransactionRepository extends JpaRepository<SystemWalletTransaction, Long> {

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM inspection_hub_payouts", nativeQuery = true)
    BigDecimal getTotalHubPaid();

    @Query("""
            SELECT COALESCE(SUM(t.heldDelta), 0)
            FROM SystemWalletTransaction t WHERE t.inspectionOrderId IS NOT NULL
            """)
    BigDecimal getInspectionHeldBalance();

    @Query("""
            SELECT COALESCE(t.orderId, t.inspectionOrderId) AS orderId,
                   t.inspectionOrderId AS inspectionOrderId, SUM(t.shippingDelta) AS shippingFee
            FROM SystemWalletTransaction t
            WHERE (t.orderId IN :orderIds OR t.inspectionOrderId IN :orderIds)
              AND t.shippingDelta > 0
              AND t.type IN (OPENING_BALANCE, PAYMENT_HOLD, RETURN_SHIPPING_FEE, INSPECTION_HOLD)
            GROUP BY t.orderId, t.inspectionOrderId
            """)
    List<ShippingPayment> findShippingPayments(@Param("orderIds") Collection<UUID> orderIds);

    interface ShippingPayment {
        UUID getOrderId();
        UUID getInspectionOrderId();
        BigDecimal getShippingFee();
    }

    boolean existsByInspectionOrderId(UUID inspectionOrderId);

    @Query("""
            SELECT COALESCE(SUM(t.heldDelta), 0) AS held, COALESCE(SUM(t.shippingDelta), 0) AS shipping
            FROM SystemWalletTransaction t
            WHERE t.inspectionOrderId = :inspectionOrderId
            """)
    InspectionBalances getInspectionBalances(@Param("inspectionOrderId") UUID inspectionOrderId);

    interface InspectionBalances {
        BigDecimal getHeld();
        BigDecimal getShipping();
    }
}
