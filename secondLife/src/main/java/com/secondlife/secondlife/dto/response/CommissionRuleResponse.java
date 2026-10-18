package com.secondlife.secondlife.dto.response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record CommissionRuleResponse(UUID id, String name, BigDecimal rate,
        BigDecimal minCommission, BigDecimal maxCommission, boolean active, long revision,
        UUID updatedBy, Instant createdAt, Instant updatedAt) {

}
