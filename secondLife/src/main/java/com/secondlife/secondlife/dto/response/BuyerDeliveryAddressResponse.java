package com.secondlife.secondlife.dto.response;

public record BuyerDeliveryAddressResponse(
        String id,
        String name,
        String phone,
        String email,
        String streetAddress,
        String city,
        Double latitude,
        Double longitude,
        String addressType,
        boolean isDefault
) {
}
