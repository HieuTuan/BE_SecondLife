package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.UserPolicyAcceptanceEvent;
import com.secondlife.secondlife.enums.PolicyAcceptanceContext;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface UserPolicyAcceptanceEventRepository extends JpaRepository<UserPolicyAcceptanceEvent, UUID> {

    boolean existsByUserIdAndPolicyVersionIdAndContextAndAcceptedAtAfter(
            UUID userId,
            UUID policyVersionId,
            PolicyAcceptanceContext context,
            Instant since
    );
}
