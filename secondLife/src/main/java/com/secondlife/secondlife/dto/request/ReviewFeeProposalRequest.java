package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;

public record ReviewFeeProposalRequest(
    @NotNull(message = "Trường approved là bắt buộc (true/false)")
    Boolean approved,
    String reason
) {
}
