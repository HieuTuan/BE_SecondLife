package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletWithdrawalReviewRequest {

    @NotBlank(message = "Action is required (APPROVE or REJECT)")
    @Pattern(regexp = "^(APPROVE|REJECT)$", message = "Action must be APPROVE or REJECT")
    private String action;

    private String adminNote;
}
