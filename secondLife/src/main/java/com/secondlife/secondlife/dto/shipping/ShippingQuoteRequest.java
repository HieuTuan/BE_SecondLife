package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ShippingQuoteRequest(@NotNull UUID postId, UUID negotiationId, @NotNull @Valid ShippingAddressRequest deliveryAddress,
        @Pattern(regexp="[A-Za-z0-9_]{1,50}") String serviceType) {
    public ShippingQuoteRequest(UUID postId,UUID negotiationId,ShippingAddressRequest address) { this(postId,negotiationId,address,null); }
}
