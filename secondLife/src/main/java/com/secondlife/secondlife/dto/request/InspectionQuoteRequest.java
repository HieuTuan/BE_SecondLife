package com.secondlife.secondlife.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record InspectionQuoteRequest(
    @NotNull(message = "postId is required")
    UUID postId,
    @Valid
    ShippingAddressRequest buyerAddress,
    BigDecimal buyerLatitude,
    BigDecimal buyerLongitude
) {
}
