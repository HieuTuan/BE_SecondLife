package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.dto.response.ReturnShippingDeductionResponse;
import com.secondlife.secondlife.entity.ReturnShippingFee;
import com.secondlife.secondlife.enums.ReturnShippingFeeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnShippingFeeRepository extends JpaRepository<ReturnShippingFee, UUID> {
    Page<ReturnShippingFee> findBySellerId(UUID sellerId, Pageable pageable);

    Page<ReturnShippingFee> findBySellerIdAndStatus(UUID sellerId, ReturnShippingFeeStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ReturnShippingFee f where f.id = :shipmentId")
    Optional<ReturnShippingFee> lockByShipmentId(@Param("shipmentId") UUID shipmentId);

    @Query(value = """
            SELECT f.id FROM return_shipping_fees f
            JOIN user_wallets w ON w.user_id = f.seller_id
            WHERE f.status = 'OUTSTANDING' AND f.debt_amount > 0 AND w.balance > 0
              AND (CAST(:afterId AS uuid) IS NULL OR f.id > :afterId)
            ORDER BY f.id
            """, nativeQuery = true)
    List<UUID> findCollectibleDebtIds(@Param("afterId") UUID afterId, Pageable pageable);

    @Query("""
            select new com.secondlife.secondlife.dto.response.ReturnShippingDeductionResponse(
                t.id, f.orderId, -t.amount, t.createdAt)
            from WalletTransaction t join ReturnShippingFee f on t.referenceId = f.id
            where f.sellerId = :sellerId and t.wallet.user.id = :sellerId
              and t.type = RETURN_SHIPPING_FEE and t.amount < 0
              and (:outstandingOnly = false or f.status = OUTSTANDING)
            order by t.createdAt desc, t.id desc
            """)
    Page<ReturnShippingDeductionResponse> findDeductions(@Param("sellerId") UUID sellerId,
            @Param("outstandingOnly") boolean outstandingOnly, Pageable pageable);

    @Query("select coalesce(sum(f.debtAmount), 0) from ReturnShippingFee f where f.sellerId = :sellerId")
    BigDecimal outstandingDebt(@Param("sellerId") UUID sellerId);
}
