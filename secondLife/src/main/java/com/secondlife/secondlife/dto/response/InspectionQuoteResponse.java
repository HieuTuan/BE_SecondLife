package com.secondlife.secondlife.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record InspectionQuoteResponse(
    UUID postId,
    String postTitle,
    BigDecimal postPrice,
    // Phí cơ sở (hỗ trợ cả baseInspectionFee và baseFee alias)
    BigDecimal baseInspectionFee,
    // Gói Tại Nhà (AT_HOME) - Flat fields
    BigDecimal travelDistanceKm,
    BigDecimal travelFeeOneWay,
    BigDecimal travelFeeRoundTrip, // travelFeeOneWay * 2
    BigDecimal atHomeTotalFee,     // baseInspectionFee + travelFeeRoundTrip
    List<InspectionTravelTierDto> appliedTiers,
    Map<String, BigDecimal> atHomeSplitOptions, // BUYER_PAYS: fee, SELLER_PAYS: fee, SPLIT_50_50: fee/2
    // Gói Tại Trung Tâm (AT_CENTER) - Flat fields
    BigDecimal shippingToCenterFee,
    BigDecimal returnShippingReserveFee,
    BigDecimal atCenterTotalFee,   // baseInspectionFee + shippingToCenterFee + returnShippingReserveFee
    Map<String, BigDecimal> atCenterSplitOptions, // BUYER_PAYS, SELLER_PAYS, SPLIT_50_50
    // Gói cấu trúc lồng nhau (Nested objects theo BE_INSPECTION_ACTION_PLAN.md)
    AtHomePackageDto atHomePackage,
    AtCenterPackageDto atCenterPackage
) {

    @JsonProperty("baseFee")
    public BigDecimal baseFee() {
        return baseInspectionFee;
    }

    @Builder
    public record AtHomePackageDto(
        BigDecimal distanceKm,
        BigDecimal travelFeeOneWay,
        BigDecimal travelFeeRoundTrip,
        BigDecimal totalFee,
        BigDecimal suggestedSplit50,
        List<?> tiersBreakdown
    ) {
    }

    @Builder
    public record AtCenterPackageDto(
        // Gói Tại Trung Tâm (AT_CENTER) - Flat fields
    BigDecimal shippingToCenterFee,
        BigDecimal returnReserveFee,
        BigDecimal totalFee,
        BigDecimal suggestedSplit50
    ) {
    }
}
