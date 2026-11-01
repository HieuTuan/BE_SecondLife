package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;

public record TopupPackageStatusRequest(@NotNull Boolean active) {
}
