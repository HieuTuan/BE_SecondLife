package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record WalletWithdrawalDisputeRequest(
        @NotBlank String type,
        @NotBlank String description,
        List<String> evidenceUrls
) {
    public WalletWithdrawalDisputeRequest(String type, String description) {
        this(type, description, List.of());
    }
}
