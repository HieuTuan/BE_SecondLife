package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InspectionBuyerRespondRequest(
        @NotBlank(message = "action is required (ACCEPT, REJECT, or COUNTER)")
        @Pattern(regexp = "^(ACCEPT|REJECT|COUNTER)$", message = "action must be ACCEPT, REJECT, or COUNTER")
        String action,

        InspectionFeePayer counterFeePayer,

        @JsonAlias({"buyerConsentZinLoss", "buyerConsentZin"})
        Boolean buyerConsentZin,

        String note
) {
}

