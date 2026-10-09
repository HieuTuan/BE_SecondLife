package com.secondlife.secondlife.dto.commission;
import com.secondlife.secondlife.entity.CommissionRuleAudit;
import java.time.Instant;
import java.util.UUID;
public record CommissionAuditResponse(UUID id, UUID ruleId, UUID actorId, String action,
        String oldValue, String newValue, String reason, Instant changedAt) {
    public static CommissionAuditResponse from(CommissionRuleAudit a) {
        return new CommissionAuditResponse(a.getId(), a.getRuleId(), a.getActorId(), a.getAction(),
                a.getOldValue(), a.getNewValue(), a.getReason(), a.getChangedAt());
    }
}
