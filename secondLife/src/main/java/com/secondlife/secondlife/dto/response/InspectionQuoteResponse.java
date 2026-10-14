package com.secondlife.secondlife.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionQuoteResponse {
    private UUID postId;
    private String postTitle;
    private BigDecimal postPrice;

    // Phí cơ sở (hỗ trợ cả baseInspectionFee và baseFee alias)
    private BigDecimal baseInspectionFee;

    public BigDecimal getBaseFee() {
        return baseInspectionFee;
    }

    // Gói Tại Nhà (AT_HOME) - Flat fields
    private BigDecimal travelDistanceKm;
    private BigDecimal travelFeeOneWay;
    private BigDecimal travelFeeRoundTrip; // travelFeeOneWay * 2
    private BigDecimal atHomeTotalFee;     // baseInspectionFee + travelFeeRoundTrip
    private List<InspectionTravelTierDto> appliedTiers;
    private Map<String, BigDecimal> atHomeSplitOptions; // BUYER_PAYS: fee, SELLER_PAYS: fee, SPLIT_50_50: fee/2

    // Gói Tại Trung Tâm (AT_CENTER) - Flat fields
    private BigDecimal shippingToCenterFee;
    private BigDecimal returnShippingReserveFee;
    private BigDecimal atCenterTotalFee;   // baseInspectionFee + shippingToCenterFee + returnShippingReserveFee
    private Map<String, BigDecimal> atCenterSplitOptions; // BUYER_PAYS, SELLER_PAYS, SPLIT_50_50

    // Gói cấu trúc lồng nhau (Nested objects theo BE_INSPECTION_ACTION_PLAN.md)
    private AtHomePackageDto atHomePackage;
    private AtCenterPackageDto atCenterPackage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtHomePackageDto {
        private BigDecimal distanceKm;
        private BigDecimal travelFeeOneWay;
        private BigDecimal travelFeeRoundTrip;
        private BigDecimal totalFee;
        private BigDecimal suggestedSplit50;
        private List<?> tiersBreakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AtCenterPackageDto {
        private BigDecimal shippingToCenterFee;
        private BigDecimal returnReserveFee;
        private BigDecimal totalFee;
        private BigDecimal suggestedSplit50;
    }
}
