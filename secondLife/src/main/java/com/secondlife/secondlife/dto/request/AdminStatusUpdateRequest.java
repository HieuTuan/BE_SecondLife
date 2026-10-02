package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;

public record AdminStatusUpdateRequest(
    @NotNull(message = "Account status is required")
    AccountStatus status
) {}
