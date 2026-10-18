package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.UUID;

@JsonIgnoreProperties({"serviceType"})
public record ShippingQuoteRequest(@NotNull UUID postId, UUID negotiationId,
        @NotNull @Valid ShippingAddressRequest deliveryAddress) { }
