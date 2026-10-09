package com.secondlife.secondlife.dto.credit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateCreditPurchaseRequest(
        @NotNull @Min(0) Integer listingQuantity,
        @NotNull @Min(0) Integer valuationQuantity,
        @NotNull @Min(0) Integer aiChatQuantity) {
}
