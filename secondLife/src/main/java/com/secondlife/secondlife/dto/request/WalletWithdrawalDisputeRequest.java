package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;

public record WalletWithdrawalDisputeRequest(@NotBlank String type, @NotBlank String description) { }
