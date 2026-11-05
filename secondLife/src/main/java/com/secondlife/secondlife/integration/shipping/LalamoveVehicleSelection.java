package com.secondlife.secondlife.integration.shipping;

import java.util.List;

/** Carrier selection diagnostics, separate from the native quotation used for booking. */
public record LalamoveVehicleSelection(
        String preferredServiceType,
        String selectedServiceType,
        List<RejectedVehicle> rejectedVehicles
) {
    public LalamoveVehicleSelection {
        rejectedVehicles = List.copyOf(rejectedVehicles);
    }

    public record RejectedVehicle(String serviceType, String providerErrorCode) {}
}
