package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.PolicyVersion;
import com.secondlife.secondlife.enums.PolicyType;
import com.secondlife.secondlife.enums.PolicyVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, UUID> {

    Optional<PolicyVersion> findByPolicyTypeAndStatus(PolicyType policyType, PolicyVersionStatus status);

    Optional<PolicyVersion> findByIdAndStatusIn(UUID id, Collection<PolicyVersionStatus> statuses);

    List<PolicyVersion> findByPolicyTypeAndStatusInOrderByPublishedAtDesc(PolicyType policyType, Collection<PolicyVersionStatus> statuses);

    boolean existsByPolicyType(PolicyType policyType);

    boolean existsByPolicyTypeAndVersion(PolicyType policyType, String version);
}
