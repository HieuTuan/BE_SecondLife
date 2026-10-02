package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CreditPricingRule;
import com.secondlife.secondlife.enums.CreditType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreditPricingRuleRepository extends JpaRepository<CreditPricingRule, UUID> {
    Optional<CreditPricingRule> findByCreditType(CreditType creditType);
}
