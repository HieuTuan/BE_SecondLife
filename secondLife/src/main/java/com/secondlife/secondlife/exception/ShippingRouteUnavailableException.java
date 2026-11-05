package com.secondlife.secondlife.exception;

import java.util.List;

/** All capacity-compatible vehicles were explicitly rejected as outside their service area. */
public final class ShippingRouteUnavailableException extends BadRequestException {
    private final String preferredServiceType;
    private final List<String> attemptedServiceTypes;
    private final String pickupCity;
    private final String deliveryCity;

    public ShippingRouteUnavailableException(String preferredServiceType, List<String> attemptedServiceTypes) {
        this(preferredServiceType, attemptedServiceTypes, null, null);
    }

    private ShippingRouteUnavailableException(String preferredServiceType, List<String> attemptedServiceTypes,
                                               String pickupCity, String deliveryCity) {
        super("Không tìm được phương tiện Lalamove phù hợp phục vụ "
                + (pickupCity != null && deliveryCity != null
                ? "tuyến từ " + pickupCity + " đến " + deliveryCity : "tuyến giao hàng đã chọn")
                + ". Vui lòng kiểm tra lại địa chỉ lấy hàng và địa chỉ nhận hàng.");
        this.preferredServiceType = preferredServiceType;
        this.attemptedServiceTypes = List.copyOf(attemptedServiceTypes);
        this.pickupCity = pickupCity;
        this.deliveryCity = deliveryCity;
    }

    public ShippingRouteUnavailableException withCities(String pickupCity, String deliveryCity) {
        return new ShippingRouteUnavailableException(preferredServiceType, attemptedServiceTypes, pickupCity, deliveryCity);
    }

    public String getPreferredServiceType() { return preferredServiceType; }
    public List<String> getAttemptedServiceTypes() { return attemptedServiceTypes; }
    public String getPickupCity() { return pickupCity; }
    public String getDeliveryCity() { return deliveryCity; }
}
