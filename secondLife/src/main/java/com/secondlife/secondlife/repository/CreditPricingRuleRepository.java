package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CreditPricingRule;
import com.secondlife.secondlife.enums.CreditType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CreditPricingRuleRepository extends JpaRepository<CreditPricingRule, UUID> {
    Optional<CreditPricingRule> findByCreditType(CreditType creditType);

    @Modifying
    @Query(value = """
            INSERT INTO credit_pricing_rules(id,credit_type,unit_price,currency,active,updated_at)
            VALUES (:id,:#{#creditType.name()},NULL,'VND',false,CURRENT_TIMESTAMP)
            ON CONFLICT (credit_type) DO NOTHING
            """, nativeQuery = true)
    int ensureRuleExists(@Param("id") UUID id, @Param("creditType") CreditType creditType);
}
