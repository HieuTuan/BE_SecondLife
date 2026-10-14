package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionSellerRespondRequest {

    @NotBlank(message = "Action is required")
    @Pattern(regexp = "^(ACCEPT|REJECT|COUNTER)$", message = "Action must be ACCEPT, REJECT, or COUNTER")
    private String action;

    private InspectionOrderType counterOrderType;
    private InspectionFeePayer counterFeePayer;

    @JsonAlias({"sellerConsentZinLoss", "sellerConsentZin"})
    private boolean sellerConsentZin;

    @JsonAlias({"rejectReason", "rejectionReason"})
    private String rejectionReason;

    public String getRejectReason() {
        return rejectionReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectionReason = rejectReason;
    }

    @JsonProperty("sellerConsentZinLoss")
    public boolean isSellerConsentZinLoss() {
        return sellerConsentZin;
    }

    public void setSellerConsentZinLoss(boolean sellerConsentZinLoss) {
        this.sellerConsentZin = sellerConsentZinLoss;
    }
}
