package com.secondlife.secondlife.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.secondlife.secondlife.common.ErrorResponse;
import com.secondlife.secondlife.common.ErrorResponse.FieldErrorItem;
import com.secondlife.secondlife.exception.ShippingRouteUnavailableException;

import java.time.LocalDateTime;
import java.util.List;

/** Retains the standard error envelope and adds structured shipping diagnostics. */
public record ShippingRouteErrorResponse(
        boolean success,
        String message,
        String path,
        List<FieldErrorItem> errors,
        LocalDateTime timestamp,
        String code,
        Details details
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Details(String preferredServiceType, List<String> attemptedServiceTypes,
                          String pickupCity, String deliveryCity, String providerErrorCode) {}

    public static ShippingRouteErrorResponse of(ShippingRouteUnavailableException error, String path) {
        ErrorResponse envelope = ErrorResponse.of(error.getMessage(), path);
        return new ShippingRouteErrorResponse(envelope.success(), envelope.message(), envelope.path(),
                envelope.errors(), envelope.timestamp(), "NO_SUITABLE_VEHICLE_FOR_ROUTE",
                new Details(error.getPreferredServiceType(), error.getAttemptedServiceTypes(),
                        error.getPickupCity(), error.getDeliveryCity(), "ERR_OUT_OF_SERVICE_AREA"));
    }
}
