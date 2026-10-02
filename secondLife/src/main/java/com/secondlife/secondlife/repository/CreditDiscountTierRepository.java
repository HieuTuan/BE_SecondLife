package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CreditDiscountTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditDiscountTierRepository extends JpaRepository<CreditDiscountTier, UUID> {
    List<CreditDiscountTier> findByActiveTrueOrderByMinQuantityAsc();
    List<CreditDiscountTier> findAllByOrderByMinQuantityAsc();
}
