package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record InspectionSellerRespondRequest(
    @NotBlank(message = "Action is required")
    @Pattern(regexp = "^(ACCEPT|REJECT|COUNTER)$", message = "Action must be ACCEPT, REJECT, or COUNTER")
    String action,
    InspectionOrderType counterOrderType,
    InspectionFeePayer counterFeePayer,
    @JsonAlias({"sellerConsentZinLoss", "sellerConsentZin"})
    boolean sellerConsentZin,
    @JsonAlias({"rejectReason", "rejectionReason"})
    String rejectionReason,
    BigDecimal counterBuyerFeePercent
) {
    public InspectionSellerRespondRequest(String action, InspectionOrderType counterOrderType,
            InspectionFeePayer counterFeePayer, boolean sellerConsentZin, String rejectionReason) {
        this(action, counterOrderType, counterFeePayer, sellerConsentZin, rejectionReason, null);
    }

    @JsonProperty("rejectReason")
    public String rejectReason() {
        return rejectionReason;
    }

    @JsonProperty("sellerConsentZinLoss")
    public boolean sellerConsentZinLoss() {
        return sellerConsentZin;
    }

}
