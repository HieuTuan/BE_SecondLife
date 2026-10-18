package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.secondlife.secondlife.enums.OrderPaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record OrderRequestDTO(
    @NotNull(message = "Payment method is required")
    @Schema(description="WALLET uses the existing balance; BANK_TRANSFER returns bank transfer instructions", example="BANK_TRANSFER", defaultValue="WALLET")
    OrderPaymentMethod paymentMethod,
    @Schema(description="Server quote ID returned by /api/v1/shipping/quotes; required for new shipped orders")
    @NotNull(message = "Shipping quote ID is required")
    UUID shippingQuoteId,
    @Schema(description="Reuse the same UUID on order retries")
    @NotNull(message = "Request ID is required")
    UUID requestId,
    @NotNull(message = "Post ID is required")
    UUID postId,
    // Optional: Only if buying with a negotiated price
    UUID negotiationId
) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static OrderRequestDTO fromJson(OrderRequestDTOBuilder builder) {
        return builder.build();
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class OrderRequestDTOBuilder {
        private OrderPaymentMethod paymentMethod = OrderPaymentMethod.WALLET;
    }
}
