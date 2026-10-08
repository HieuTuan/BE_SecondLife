package com.secondlife.secondlife.dto.commission;
import com.secondlife.secondlife.entity.CommissionRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record CommissionRuleResponse(UUID id, String name, BigDecimal rate,
        BigDecimal minCommission, BigDecimal maxCommission, boolean active, long revision,
        UUID updatedBy, Instant createdAt, Instant updatedAt) {
    public static CommissionRuleResponse from(CommissionRule rule) {
        return new CommissionRuleResponse(rule.getId(), rule.getName(), rule.getRate(), rule.getMinCommission(),
                rule.getMaxCommission(), rule.isActive(), rule.getRevision(), rule.getUpdatedBy(), rule.getCreatedAt(), rule.getUpdatedAt());
    }
}
