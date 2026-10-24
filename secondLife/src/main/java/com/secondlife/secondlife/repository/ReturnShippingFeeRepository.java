package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.ReturnShippingFee;
import com.secondlife.secondlife.enums.ReturnShippingFeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface ReturnShippingFeeRepository extends JpaRepository<ReturnShippingFee, UUID> {
    Page<ReturnShippingFee> findBySellerId(UUID sellerId, Pageable pageable);

    Page<ReturnShippingFee> findBySellerIdAndStatus(UUID sellerId, ReturnShippingFeeStatus status, Pageable pageable);

    @Query("select coalesce(sum(f.debtAmount), 0) from ReturnShippingFee f where f.sellerId = :sellerId")
    BigDecimal outstandingDebt(@Param("sellerId") UUID sellerId);
}
