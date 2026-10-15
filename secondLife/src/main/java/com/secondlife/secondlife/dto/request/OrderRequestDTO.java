package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class OrderRequestDTO {
    @NotNull(message = "Payment method is required")
    @io.swagger.v3.oas.annotations.media.Schema(description="WALLET uses the existing balance; BANK_TRANSFER returns bank transfer instructions", example="BANK_TRANSFER", defaultValue="WALLET")
    private com.secondlife.secondlife.enums.OrderPaymentMethod paymentMethod = com.secondlife.secondlife.enums.OrderPaymentMethod.WALLET;
    @io.swagger.v3.oas.annotations.media.Schema(description="Server quote ID returned by /api/v1/shipping/quotes; required for new shipped orders")
    @NotNull(message = "Shipping quote ID is required")
    private UUID shippingQuoteId;
    @io.swagger.v3.oas.annotations.media.Schema(description="Reuse the same UUID on order retries")
    @NotNull(message = "Request ID is required")
    private UUID requestId;
    @NotNull(message = "Post ID is required")
    private UUID postId;

    // Optional: Only if buying with a negotiated price
    private UUID negotiationId;
}
