package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record InspectionCenterOptionResponse(UUID id, String name, String phone, String address,
        BigDecimal latitude, BigDecimal longitude, boolean shippingAddressConfigured) {
}
