package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InspectionShippingQuoteResponse(
        UUID id, UUID inspectionCenterId, Instant expiresAt, Leg outbound, Leg returnReserve
) {
    public record Leg(String leg, String provider, BigDecimal shippingFee, String serviceType,
            String providerQuotationId, Instant expiresAt, ShippingVehicleSelectionResponse vehicleSelection) {
    }
}
