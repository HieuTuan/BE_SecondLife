package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.PolicyType;

import java.time.Instant;
import java.util.UUID;

public record PolicyAcceptanceStatusResponse(
        PolicyType policyType,
        UUID activePolicyVersionId,
        String activeVersion,
        String activeTitle,
        Instant publishedAt,
        Instant effectiveAt,
        boolean requiresAcceptance,
        boolean acceptedCurrentVersion,
        Instant acceptedAt,
        Instant lastConfirmedAt,
        String lastAcceptedVersion,
        Instant lastAcceptedAt
) {}
