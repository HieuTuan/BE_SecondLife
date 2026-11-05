package com.secondlife.secondlife.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShippingVehicleSelectionResponse(
        @Schema(description = "Smallest suitable vehicle attempted first; not necessarily the cheapest")
        String preferredServiceType,
        String selectedServiceType,
        boolean fallbackUsed,
        @Schema(description = "PREFERRED_VEHICLE_OUT_OF_SERVICE_AREA or PREFERRED_VEHICLE_UNAVAILABLE; absent without fallback")
        String reasonCode,
        String providerErrorCode,
        @Schema(description = "City determined from actual pickup coordinates, including an inspection center when applicable")
        String pickupCity,
        String deliveryCity,
        @Schema(description = "Distance reported by the carrier, converted to kilometers; absent if unavailable")
        BigDecimal distanceKm,
        String message
) {}
