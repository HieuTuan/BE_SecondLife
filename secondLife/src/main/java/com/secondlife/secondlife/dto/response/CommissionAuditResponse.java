package com.secondlife.secondlife.dto.response;
import java.time.Instant;
import java.util.UUID;
public record CommissionAuditResponse(UUID id, UUID ruleId, UUID actorId, String action,
        String oldValue, String newValue, String reason, Instant changedAt) {

}
