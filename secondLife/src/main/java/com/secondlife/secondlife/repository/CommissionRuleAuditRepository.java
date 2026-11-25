package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.CommissionRuleAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
public interface CommissionRuleAuditRepository extends JpaRepository<CommissionRuleAudit, UUID> {
    @Query(value = """
            select audit as audit, profile.fullName as actorName
            from CommissionRuleAudit audit
            left join UserProfile profile on profile.user.id = audit.actorId
            where audit.ruleId = :ruleId
            order by audit.changedAt desc, audit.id desc
            """, countQuery = """
            select count(audit) from CommissionRuleAudit audit where audit.ruleId = :ruleId
            """)
    Page<HistoryRow> findHistory(@Param("ruleId") UUID ruleId, Pageable pageable);

    interface HistoryRow {
        CommissionRuleAudit getAudit();
        String getActorName();
    }
}
