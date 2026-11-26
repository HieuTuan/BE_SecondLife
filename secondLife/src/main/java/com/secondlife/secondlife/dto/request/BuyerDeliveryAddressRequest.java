package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BuyerDeliveryAddressRequest(
        String id,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 255) String email,
        @NotBlank @Size(max = 500) String streetAddress,
        @Size(max = 100) String city,
        @NotNull Double latitude,
        @NotNull Double longitude,
        @Size(max = 50) String addressType,
        Boolean isDefault
) {
}
