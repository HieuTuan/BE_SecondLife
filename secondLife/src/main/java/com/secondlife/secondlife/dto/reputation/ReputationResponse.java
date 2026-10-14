package com.secondlife.secondlife.dto.reputation;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record ReputationResponse(UUID sellerId,@JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal score,
        long goodTransactions,long badTransactions,long evaluatedOrders,int minimumEvaluatedOrders,
        String assessmentStatus,long confirmedIncidents,long seriousIncidents,boolean sellingRestricted,String warningCode,
        Instant updatedAt,String formulaVersion,long sellerCancellationOrders,long sellerReturnFaultOrders,
        long nonFulfillmentOrders,BigDecimal nonFulfillmentRate,long handoverEvaluatedOrders,
        long lateHandoverOrders,BigDecimal lateHandoverRate,
        @JsonInclude(JsonInclude.Include.ALWAYS) BigDecimal transactionScore,BigDecimal confirmationPenaltyPoints) { }
