package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectorStatsResponse {
    private UUID inspectorId;
    private String inspectorName;
    private String badgeCode;
    private String skillLevel;
    private int completedJobs;
    private int passedJobs;
    private int failedJobs;
    private double averageRating;
    private double onTimeRate;
    private BigDecimal monthlyEarnings;
    private BigDecimal laborFeeEarned;
    private BigDecimal travelAllowanceEarned;
    private BigDecimal pendingSettlementAmount;
}
