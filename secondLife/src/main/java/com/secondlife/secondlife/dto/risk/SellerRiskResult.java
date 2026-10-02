package com.secondlife.secondlife.dto.risk;

import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.RiskStatus;

import java.util.List;

public record SellerRiskResult(
        RiskStatus status,
        List<ReasonCode> reasonCodes,
        Double riskScore,
        String summary
) {
    public static SellerRiskResult clear() {
        return new SellerRiskResult(RiskStatus.CLEAR, List.of(ReasonCode.NONE), 0.0, "Không phát hiện dấu hiệu rủi ro");
    }

    public static SellerRiskResult review(List<ReasonCode> reasonCodes, Double riskScore, String summary) {
        return new SellerRiskResult(RiskStatus.REVIEW, reasonCodes, riskScore, summary);
    }

    public static SellerRiskResult block(List<ReasonCode> reasonCodes, String summary) {
        return new SellerRiskResult(RiskStatus.BLOCK, reasonCodes, 100.0, summary);
    }
}
