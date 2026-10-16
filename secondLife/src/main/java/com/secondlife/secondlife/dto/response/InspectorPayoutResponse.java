package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionOrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectorPayoutResponse {
    private String payoutId;
    private UUID orderId;
    private String postTitle;
    private InspectionOrderType orderType;
    private Instant completedAt;
    private BigDecimal laborFee;
    private BigDecimal travelFee;
    private BigDecimal totalPayout;
    private String status;
}
