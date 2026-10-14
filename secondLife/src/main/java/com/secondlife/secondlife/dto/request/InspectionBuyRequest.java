package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.secondlife.secondlife.dto.shipping.ShippingAddressRequest;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionBuyRequest {

    @NotNull(message = "postId is required")
    private UUID postId;

    @NotNull(message = "orderType is required (AT_HOME or AT_CENTER)")
    private InspectionOrderType orderType;

    @NotNull(message = "feePayer is required (BUYER_PAYS, SELLER_PAYS, SPLIT_50_50)")
    private InspectionFeePayer feePayer;

    @AssertTrue(message = "Bạn cần đồng ý với điều khoản tháo máy kiểm định có thể làm mất trạng thái zin ban đầu")
    @JsonAlias({"buyerConsentZinLoss", "buyerConsentZin"})
    private boolean buyerConsentZin;

    @Valid
    private ShippingAddressRequest buyerDeliveryAddress;

    private String note;
    private java.math.BigDecimal buyerLatitude;
    private java.math.BigDecimal buyerLongitude;

    public java.math.BigDecimal getBuyerLatitude() {
        if (buyerLatitude != null) return buyerLatitude;
        return buyerDeliveryAddress != null ? buyerDeliveryAddress.latitude() : null;
    }

    public java.math.BigDecimal getBuyerLongitude() {
        if (buyerLongitude != null) return buyerLongitude;
        return buyerDeliveryAddress != null ? buyerDeliveryAddress.longitude() : null;
    }

    @JsonProperty("buyerConsentZinLoss")
    public boolean isBuyerConsentZinLoss() {
        return buyerConsentZin;
    }

    public void setBuyerConsentZinLoss(boolean buyerConsentZinLoss) {
        this.buyerConsentZin = buyerConsentZinLoss;
    }
}
