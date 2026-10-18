package com.secondlife.secondlife.dto.response;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record ReputationHistoryResponse(UUID id, String eventType, UUID orderId, UUID reportId,
        long goodBefore, long badBefore, long goodAfter, long badAfter, BigDecimal scoreBefore,
        BigDecimal scoreAfter, Long priorGood, Long priorTotal, Instant createdAt, String formulaVersion,BigDecimal penaltyBefore,BigDecimal penaltyAfter,Long eventSequence) { }
