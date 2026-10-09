package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.CommissionRuleAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface CommissionRuleAuditRepository extends JpaRepository<CommissionRuleAudit, UUID> {
    Page<CommissionRuleAudit> findByRuleIdOrderByChangedAtDesc(UUID ruleId, Pageable pageable);
}
