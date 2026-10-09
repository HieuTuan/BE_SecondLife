package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.CommissionRule;
import com.secondlife.secondlife.enums.CommissionRuleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {
    Page<CommissionRule> findByType(CommissionRuleType type, Pageable pageable);
    Optional<CommissionRule> findByTypeAndActiveTrue(CommissionRuleType type);
}
