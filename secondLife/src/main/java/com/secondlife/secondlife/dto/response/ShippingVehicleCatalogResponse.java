package com.secondlife.secondlife.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Verified vehicle capacity metadata for local form validation; route availability and fees require a shipping quotation")
public record ShippingVehicleCatalogResponse(
        String provider,
        @Schema(description = "Source of each vehicle's minimum input weight; APPLICATION is not a carrier minimum", allowableValues = "APPLICATION")
        String minWeightSource,
        int minDimensionCm,
        boolean allowLengthWidthSwap,
        boolean keepUpright,
        List<CityVehicles> cities
) {
    public record CityVehicles(String locode, String name, List<Vehicle> vehicles) { }

    public record Vehicle(
            String serviceType,
            String description,
            @Schema(description = "Application minimum packed weight in grams, shared by all vehicles; not a Lalamove minimum", example = "1")
            int minWeightGram,
            @Schema(description = "Maximum packed weight in grams, normalized from the Lalamove catalogue")
            BigDecimal maxWeightGram,
            @Schema(description = "Maximum length in centimeters, normalized from the Lalamove catalogue")
            BigDecimal maxLengthCm,
            @Schema(description = "Maximum width in centimeters, normalized from the Lalamove catalogue")
            BigDecimal maxWidthCm,
            @Schema(description = "Maximum upright height in centimeters, normalized from the Lalamove catalogue")
            BigDecimal maxHeightCm
    ) { }
}
