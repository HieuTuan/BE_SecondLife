package com.secondlife.secondlife.dto.response;

public record PickupAddressUpdateStatusResponse(
        boolean updatable,
        String reason
) {
}
