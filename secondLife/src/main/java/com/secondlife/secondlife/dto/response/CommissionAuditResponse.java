package com.secondlife.secondlife.dto.response;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record CommissionAuditResponse(UUID id, UUID ruleId, UUID actorId, String action,
        String oldValue, String newValue, String reason, Instant changedAt,
        String actorName, Snapshot before, Snapshot after) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Snapshot(String name, BigDecimal rate, BigDecimal minCommission,
                           BigDecimal maxCommission, Boolean active, Long revision) {
    }

}
