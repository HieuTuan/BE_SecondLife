package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InspectionBuyerRespondRequest(
    @NotBlank(message = "action is required (ACCEPT or REJECT)")
    @Pattern(regexp = "^(ACCEPT|REJECT)$", message = "action must be ACCEPT or REJECT")
    String action,

    @JsonAlias({"buyerConsentZinLoss", "buyerConsentZin"})
    Boolean buyerConsentZin,

    String note
) {
}

