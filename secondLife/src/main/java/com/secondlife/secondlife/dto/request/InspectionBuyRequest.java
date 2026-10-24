package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InspectionBuyRequest(
    @NotNull(message = "postId is required")
    UUID postId,
    @NotNull(message = "orderType is required (AT_HOME or AT_CENTER)")
    InspectionOrderType orderType,
    @NotNull(message = "feePayer is required (BUYER_PAYS, SELLER_PAYS, SPLIT_50_50)")
    InspectionFeePayer feePayer,
    @AssertTrue(message = "Bạn cần đồng ý với điều khoản tháo máy kiểm định có thể làm mất trạng thái zin ban đầu")
    @JsonAlias({"buyerConsentZinLoss", "buyerConsentZin"})
    boolean buyerConsentZin,
    @Valid
    ShippingAddressRequest buyerDeliveryAddress,
    String note,
    BigDecimal buyerLatitude,
    BigDecimal buyerLongitude
) {
    public BigDecimal buyerLatitude() {
        if (buyerLatitude != null) return buyerLatitude;
        return buyerDeliveryAddress != null ? buyerDeliveryAddress.latitude() : null;
    }

    public BigDecimal buyerLongitude() {
        if (buyerLongitude != null) return buyerLongitude;
        return buyerDeliveryAddress != null ? buyerDeliveryAddress.longitude() : null;
    }

    @JsonProperty("buyerConsentZinLoss")
    public boolean buyerConsentZinLoss() {
        return buyerConsentZin;
    }

}
