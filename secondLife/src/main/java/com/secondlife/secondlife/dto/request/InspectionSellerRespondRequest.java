package com.secondlife.secondlife.dto.request;

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

    private boolean sellerConsentZin;
    private String rejectionReason;

    public String getRejectReason() {
        return rejectionReason;
    }

    public boolean isSellerConsentZinLoss() {
        return sellerConsentZin;
    }
}
