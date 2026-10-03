package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AiPriceEstimationRequest(@NotNull UUID requestId) {}
