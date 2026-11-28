package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AcceptPolicyRequest;
import com.secondlife.secondlife.dto.request.PublishPolicyVersionRequest;
import com.secondlife.secondlife.dto.response.PolicyAcceptanceStatusResponse;
import com.secondlife.secondlife.dto.response.PolicyVersionResponse;
import com.secondlife.secondlife.dto.response.PolicyVersionSummaryResponse;
import com.secondlife.secondlife.entity.PolicyVersion;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.PolicyAcceptanceContext;
import com.secondlife.secondlife.enums.PolicyAcceptanceSource;
import com.secondlife.secondlife.enums.PolicyType;

import java.util.List;
import java.util.UUID;

public interface PolicyService {

    PolicyVersionResponse getCurrentPolicy(PolicyType type);

    PolicyVersionResponse getPublishedPolicyVersion(UUID versionId);

    List<PolicyVersionSummaryResponse> listPublishedVersions(PolicyType type);

    PolicyVersion validateExplicitAcceptance(Boolean acceptTerms, UUID policyVersionId, PolicyType type);

    void recordAcceptance(User user, PolicyVersion version, PolicyAcceptanceSource source, PolicyAcceptanceContext context);

    PolicyAcceptanceStatusResponse getAcceptanceStatus(UUID userId, PolicyType type);

    PolicyAcceptanceStatusResponse acceptCurrentPolicy(UUID userId, AcceptPolicyRequest request);

    PolicyVersionResponse publishNewVersion(PublishPolicyVersionRequest request);

    void ensureInitialTermsSeeded();
}
