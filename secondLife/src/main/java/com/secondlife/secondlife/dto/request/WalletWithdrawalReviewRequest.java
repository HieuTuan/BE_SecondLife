package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WalletWithdrawalReviewRequest(
    @NotBlank(message = "Action is required (APPROVE or REJECT)")
    @Pattern(regexp = "^(APPROVE|REJECT)$", message = "Action must be APPROVE or REJECT")
    String action,
    String adminNote
) {
}
