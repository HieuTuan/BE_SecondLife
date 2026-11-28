package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.PolicyType;
import com.secondlife.secondlife.enums.PolicyVersionStatus;

import java.time.Instant;
import java.util.UUID;

public record PolicyVersionSummaryResponse(
        UUID id,
        PolicyType policyType,
        String title,
        String version,
        String contentHash,
        PolicyVersionStatus status,
        boolean requiresReacceptance,
        Instant publishedAt,
        Instant effectiveAt,
        Instant updatedAt
) {}
