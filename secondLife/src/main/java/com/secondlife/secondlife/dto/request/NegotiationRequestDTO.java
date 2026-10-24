package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record NegotiationRequestDTO(
    @NotNull(message = "Post ID is required")
    UUID postId,
    @NotNull(message = "Offered price is required")
    @Min(value = 1, message = "Offered price must be greater than 0")
    BigDecimal offeredPrice
) {
}
