package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewFeeProposalRequest {

    @NotNull(message = "Trường approved là bắt buộc (true/false)")
    private Boolean approved;

    private String reason;
}
