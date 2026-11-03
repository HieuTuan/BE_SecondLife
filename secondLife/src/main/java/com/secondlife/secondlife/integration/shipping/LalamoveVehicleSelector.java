package com.secondlife.secondlife.integration.shipping;

import com.secondlife.secondlife.exception.BadRequestException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects only vehicles with verified capacity; packed appliances remain upright. */
final class LalamoveVehicleSelector {
    private LalamoveVehicleSelector() { }

    static List<String> eligible(JsonNode catalogue, Map<String, Object> payload) {
        BigDecimal weight = packed(payload, "weight");
        BigDecimal length = packed(payload, "length");
        BigDecimal width = packed(payload, "width");
        BigDecimal height = packed(payload, "height");
        String locode = LalamoveVehicleCatalog.pickupCityLocode(
                coordinate(payload, "from_latitude", 90), coordinate(payload, "from_longitude", 180));
        var normalized = LalamoveVehicleCatalog.normalize(catalogue, locode);
        Map<String, BigDecimal> keys = new LinkedHashMap<>();
        Set<String> limits = new LinkedHashSet<>();
        for (var city : normalized.cities()) {
            for (var vehicle : city.vehicles()) {
                limits.add(vehicle.serviceType() + ": tối đa "
                        + vehicle.maxWeightGram().divide(BigDecimal.valueOf(1000)).stripTrailingZeros().toPlainString() + " kg, "
                        + vehicle.maxLengthCm().stripTrailingZeros().toPlainString() + " x "
                        + vehicle.maxWidthCm().stripTrailingZeros().toPlainString() + " x "
                        + vehicle.maxHeightCm().stripTrailingZeros().toPlainString() + " cm");
                if (weight.compareTo(vehicle.maxWeightGram()) > 0 || height.compareTo(vehicle.maxHeightCm()) > 0) continue;
                boolean fitsNormally = length.compareTo(vehicle.maxLengthCm()) <= 0 && width.compareTo(vehicle.maxWidthCm()) <= 0;
                boolean fitsRotated = length.compareTo(vehicle.maxWidthCm()) <= 0 && width.compareTo(vehicle.maxLengthCm()) <= 0;
                if (fitsNormally || fitsRotated) keys.merge(vehicle.serviceType(), vehicle.maxWeightGram(), BigDecimal::min);
            }
        }
        if (keys.isEmpty()) {
            throw new BadRequestException("Không có loại xe Lalamove đủ tải trọng và kích thước cho kiện hàng tại điểm lấy hàng"
                    + (limits.isEmpty() ? "" : ". Giới hạn từng loại xe tại điểm lấy hàng (dài x rộng x cao): " + String.join("; ", limits)));
        }
        return keys.entrySet().stream().sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList();
    }

    static void validateParcel(Map<String, Object> payload) {
        for (String field : List.of("weight", "length", "width", "height")) packed(payload, field);
    }

    private static BigDecimal packed(Map<String, Object> payload, String field) {
        try {
            BigDecimal value = new BigDecimal(String.valueOf(payload.get(field)));
            if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new BadRequestException("Người bán cần cung cấp cân nặng (g) và kích thước kiện hàng (cm) hợp lệ");
        }
    }

    private static BigDecimal coordinate(Map<String, Object> payload, String field, int limit) {
        try {
            BigDecimal value = new BigDecimal(String.valueOf(payload.get(field)));
            if (value.abs().compareTo(BigDecimal.valueOf(limit)) > 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            throw new BadRequestException("Điểm lấy hàng cần tọa độ hợp lệ để chọn xe vận chuyển");
        }
    }
}
