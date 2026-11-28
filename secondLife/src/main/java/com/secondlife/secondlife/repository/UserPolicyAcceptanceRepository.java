package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.UserPolicyAcceptance;
import com.secondlife.secondlife.enums.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPolicyAcceptanceRepository extends JpaRepository<UserPolicyAcceptance, UUID> {

    Optional<UserPolicyAcceptance> findByUserIdAndPolicyVersionId(UUID userId, UUID policyVersionId);

    boolean existsByUserIdAndPolicyVersionId(UUID userId, UUID policyVersionId);

    @Query("""
            select a from UserPolicyAcceptance a
            join fetch a.policyVersion pv
            where a.user.id = :userId and pv.policyType = :policyType
            order by a.firstAcceptedAt desc
            """)
    List<UserPolicyAcceptance> findHistoryByUserIdAndPolicyType(
            @Param("userId") UUID userId,
            @Param("policyType") PolicyType policyType
    );
}
