package com.secondlife.secondlife.integration.shipping;

import com.secondlife.secondlife.common.VietnameseAdministrativeAreas;
import com.secondlife.secondlife.dto.response.ShippingVehicleCatalogResponse;
import com.secondlife.secondlife.dto.response.ShippingVehicleCatalogResponse.CityVehicles;
import com.secondlife.secondlife.dto.response.ShippingVehicleCatalogResponse.Vehicle;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared normalization for catalogue responses and automatic vehicle selection. */
public final class LalamoveVehicleCatalog {
    private static final int MIN_WEIGHT_GRAM = 1;
    private static final int MIN_DIMENSION_CM = 1;
    private static final Set<String> SUPPORTED_LOCODES = Set.of("VN HAN", "VN DAD", "VN SGN");

    private LalamoveVehicleCatalog() { }

    /** Returns null for an unfiltered catalogue; coordinates must be supplied together. */
    public static String pickupCityLocode(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null && longitude == null) return null;
        if (latitude == null || longitude == null) {
            throw new BadRequestException("Cần cung cấp đồng thời latitude và longitude của điểm lấy hàng");
        }
        if (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BadRequestException("Tọa độ điểm lấy hàng không hợp lệ");
        }
        var division = VietnameseAdministrativeAreas.locate(latitude.doubleValue(), longitude.doubleValue());
        if (division == null) {
            throw new BadRequestException("Điểm lấy hàng nằm ngoài khu vực vận chuyển được hỗ trợ");
        }
        return switch (division.city()) {
            case HANOI -> "VN HAN";
            case DANANG -> "VN DAD";
            case HO_CHI_MINH -> "VN SGN";
        };
    }

    public static ShippingVehicleCatalogResponse normalize(JsonNode catalogue, String pickupLocode) {
        if (catalogue == null || !catalogue.isArray()) {
            throw new ShippingProviderException("Lalamove returned an invalid vehicle catalogue");
        }
        List<CityVehicles> cities = new ArrayList<>();
        for (JsonNode city : catalogue) {
            String locode = city.path("locode").asText().strip().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (!SUPPORTED_LOCODES.contains(locode) || pickupLocode != null && !pickupLocode.equals(locode)) continue;
            List<Vehicle> vehicles = new ArrayList<>();
            for (JsonNode service : city.path("services")) {
                Vehicle vehicle = vehicle(service);
                if (vehicle != null) vehicles.add(vehicle);
            }
            cities.add(new CityVehicles(locode, city.path("name").asText(locode), List.copyOf(vehicles)));
        }
        return new ShippingVehicleCatalogResponse("LALAMOVE", "APPLICATION", MIN_DIMENSION_CM,
                true, true, List.copyOf(cities));
    }

    private static Vehicle vehicle(JsonNode service) {
        String serviceType = service.path("key").asText();
        if (!serviceType.matches("[A-Z][A-Z0-9_]{0,59}")) return null;
        BigDecimal weight = capacity(service.path("load"), true);
        JsonNode dimensions = service.path("dimensions");
        BigDecimal length = capacity(dimensions.path("length"), false);
        BigDecimal width = capacity(dimensions.path("width"), false);
        BigDecimal height = capacity(dimensions.path("height"), false);
        if (weight == null || length == null || width == null || height == null
                || weight.compareTo(BigDecimal.valueOf(MIN_WEIGHT_GRAM)) < 0
                || length.compareTo(BigDecimal.valueOf(MIN_DIMENSION_CM)) < 0
                || width.compareTo(BigDecimal.valueOf(MIN_DIMENSION_CM)) < 0
                || height.compareTo(BigDecimal.valueOf(MIN_DIMENSION_CM)) < 0) return null;
        return new Vehicle(serviceType, service.path("description").asText(""), MIN_WEIGHT_GRAM,
                weight, length, width, height);
    }

    private static BigDecimal capacity(JsonNode field, boolean weight) {
        try {
            BigDecimal value = new BigDecimal(field.path("value").asText());
            if (value.signum() <= 0) return null;
            String unit = field.path("unit").asText().strip().toLowerCase(Locale.ROOT);
            BigDecimal multiplier = weight ? switch (unit) {
                case "g" -> BigDecimal.ONE;
                case "kg" -> BigDecimal.valueOf(1000);
                case "t" -> BigDecimal.valueOf(1000000);
                default -> null;
            } : switch (unit) {
                case "cm" -> BigDecimal.ONE;
                case "m" -> BigDecimal.valueOf(100);
                case "mm" -> new BigDecimal("0.1");
                default -> null;
            };
            return multiplier == null ? null : value.multiply(multiplier);
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
