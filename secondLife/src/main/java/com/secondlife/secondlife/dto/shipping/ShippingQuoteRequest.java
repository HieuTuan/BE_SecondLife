package com.secondlife.secondlife.dto.shipping;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"serviceType"})
public record ShippingQuoteRequest(@NotNull UUID postId, UUID negotiationId,
        @NotNull @Valid ShippingAddressRequest deliveryAddress) { }
