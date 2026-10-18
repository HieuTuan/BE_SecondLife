package com.secondlife.secondlife.repository;
import com.secondlife.secondlife.entity.CommissionRule;
import com.secondlife.secondlife.enums.CommissionRuleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;
public interface CommissionRuleRepository extends JpaRepository<CommissionRule, UUID> {
    Page<CommissionRule> findByType(CommissionRuleType type, Pageable pageable);
    Optional<CommissionRule> findByTypeAndActiveTrue(CommissionRuleType type);

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(6002246411256416595)", nativeQuery = true)
    void lockPolicy();
}
