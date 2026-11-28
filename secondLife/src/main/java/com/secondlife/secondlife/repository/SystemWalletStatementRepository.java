package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SystemWalletStatementEntry;
import com.secondlife.secondlife.enums.SystemWalletFundCategory;
import com.secondlife.secondlife.enums.SystemWalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SystemWalletStatementRepository extends JpaRepository<SystemWalletStatementEntry, String> {

    @Query("""
            SELECT t FROM SystemWalletStatementEntry t
            WHERE (:type IS NULL OR t.type = :type)
              AND (:orderId IS NULL OR t.orderId = :orderId OR t.topupPackagePurchaseId = :orderId)
              AND (:fundCategory IS NULL OR t.fundCategory = :fundCategory)
              AND (:hasShipping IS NULL
                   OR (:hasShipping = TRUE AND t.shippingDelta <> 0)
                   OR (:hasShipping = FALSE AND t.shippingDelta = 0))
            """)
    Page<SystemWalletStatementEntry> search(
            @Param("type") SystemWalletTransactionType type,
            @Param("hasShipping") Boolean hasShipping,
            @Param("orderId") UUID orderId,
            @Param("fundCategory") SystemWalletFundCategory fundCategory,
            Pageable pageable);
}
