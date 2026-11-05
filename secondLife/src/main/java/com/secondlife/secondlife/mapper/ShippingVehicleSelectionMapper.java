package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.common.VietnameseAdministrativeAreas;
import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.ShippingVehicleSelectionResponse;
import com.secondlife.secondlife.integration.shipping.LalamoveVehicleSelection;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;

@Component
public class ShippingVehicleSelectionMapper {
    private final Delegate delegate;

    public ShippingVehicleSelectionMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ShippingVehicleSelectionResponse toResponse(JsonNode preview, ShippingAddress origin, ShippingAddress delivery) {
        JsonNode metadata = preview.path("vehicle_selection");
        if (!metadata.isObject()) return null;

        var rejectedVehicles = new ArrayList<LalamoveVehicleSelection.RejectedVehicle>();
        for (JsonNode item : metadata.path("rejectedVehicles")) {
            rejectedVehicles.add(new LalamoveVehicleSelection.RejectedVehicle(
                    item.path("serviceType").asText(), item.path("providerErrorCode").asText()));
        }
        var selection = new LalamoveVehicleSelection(metadata.path("preferredServiceType").asText(),
                metadata.path("selectedServiceType").asText(), rejectedVehicles);
        var rejection = selection.rejectedVehicles().stream()
                .filter(item -> item.serviceType().equals(selection.preferredServiceType()))
                .findFirst();
        boolean fallbackUsed = !selection.preferredServiceType().equals(selection.selectedServiceType());
        String providerErrorCode = rejection.map(LalamoveVehicleSelection.RejectedVehicle::providerErrorCode).orElse(null);
        String reasonCode = reasonCode(fallbackUsed, providerErrorCode);
        String pickupCity = city(origin);
        String deliveryCity = city(delivery);
        String message = fallbackUsed ? explanation(selection, reasonCode, pickupCity, deliveryCity) : null;

        return delegate.toResponse(selection, fallbackUsed, reasonCode, providerErrorCode,
                pickupCity, deliveryCity, distanceKm(preview.path("provider_quote").path("distance")), message);
    }

    public String city(ShippingAddress address) {
        if (address == null || address.latitude() == null || address.longitude() == null) return null;
        var division = VietnameseAdministrativeAreas.locate(address.latitude().doubleValue(), address.longitude().doubleValue());
        return division == null ? null : division.cityName();
    }

    private String reasonCode(boolean fallbackUsed, String providerErrorCode) {
        if (!fallbackUsed) return null;
        if ("ERR_OUT_OF_SERVICE_AREA".equals(providerErrorCode)) {
            return "PREFERRED_VEHICLE_OUT_OF_SERVICE_AREA";
        }
        return "PREFERRED_VEHICLE_UNAVAILABLE";
    }

    private String explanation(LalamoveVehicleSelection selection, String reasonCode, String pickupCity, String deliveryCity) {
        String route = pickupCity != null && deliveryCity != null
                ? "tuyến từ " + pickupCity + " đến " + deliveryCity : "tuyến giao hàng đã chọn";
        String preferred = vehicleName(selection.preferredServiceType());
        String reason = "PREFERRED_VEHICLE_OUT_OF_SERVICE_AREA".equals(reasonCode)
                ? "Kiện hàng phù hợp với " + preferred + ", nhưng Lalamove không hỗ trợ loại xe này trên " + route + ". "
                : "Loại xe ưu tiên " + preferred + " hiện không khả dụng trong dịch vụ Lalamove. ";
        return reason + "Hệ thống đã chọn " + vehicleName(selection.selectedServiceType())
                + " thay thế. Vui lòng kiểm tra phí vận chuyển và địa chỉ trước khi đặt hàng.";
    }

    private String vehicleName(String serviceType) {
        return switch (serviceType) {
            case "MOTORCYCLE" -> "xe máy";
            case "LD_PICKUP_TRUCK", "PICK_UP_TRUCK", "PICKUP_TRUCK" -> "xe bán tải";
            case "LD_VAN_500", "LD_VAN_1000", "VAN", "VAN1000" -> "xe van";
            case "LD_TRUCK_500", "LD_TRUCK_1000", "LD_TRUCK_1500", "LD_TRUCK_2000",
                    "TRUCK175", "TRUCK330", "TRUCK_1500", "TRUCK550" -> "xe tải";
            default -> "phương tiện " + serviceType;
        };
    }

    private BigDecimal distanceKm(JsonNode distance) {
        try {
            BigDecimal value = new BigDecimal(distance.path("value").asText());
            if (value.signum() < 0) return null;
            return switch (distance.path("unit").asText()) {
                case "m" -> value.movePointLeft(3).stripTrailingZeros();
                case "km" -> value.stripTrailingZeros();
                default -> null;
            };
        } catch (NumberFormatException error) {
            return null;
        }
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        ShippingVehicleSelectionResponse toResponse(LalamoveVehicleSelection selection, boolean fallbackUsed,
                String reasonCode, String providerErrorCode, String pickupCity, String deliveryCity,
                BigDecimal distanceKm, String message);
    }
}
