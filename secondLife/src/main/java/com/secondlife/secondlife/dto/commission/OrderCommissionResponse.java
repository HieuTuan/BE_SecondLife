package com.secondlife.secondlife.dto.commission;
import com.secondlife.secondlife.enums.CommissionRuleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record OrderCommissionResponse(UUID snapshotId, UUID orderId, UUID ruleId, long ruleRevision, String ruleName,
        CommissionRuleType ruleType, UUID categoryId, BigDecimal transactionValueFrom, BigDecimal transactionValueTo,
        BigDecimal rate, BigDecimal minCommission, BigDecimal maxCommission, String baseType, BigDecimal commissionBase,
        BigDecimal rawCommission, BigDecimal platformCommission, BigDecimal sellerPayout, String currency,
        Instant snapshottedAt, UUID capturedBy, String reason) {}
