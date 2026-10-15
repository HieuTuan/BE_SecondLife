package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.exception.BadRequestException;
import tools.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Retains physical address details, replacing all upstream administrative labels. */
final class CurrentVietnameseAddress {
    private static final List<String> ADMIN_FIELDS = List.of("suburb", "quarter", "district", "city_district",
            "county", "city", "municipality", "state", "postcode", "country");
    private static final Pattern ADMIN_PREFIX = Pattern.compile(
            "(?iu)^(?:phường|xã|thị trấn|quận|huyện|thị xã|thành phố|tỉnh|ward|district|province)\\s+.*");
    private static final Pattern POSTCODE = Pattern.compile("(?i)^(?:[a-z]{2}\\s+)?[0-9]{5,6}$");

    private CurrentVietnameseAddress() { }

    static String format(JsonNode result, VietnameseAdministrativeAreas.Division division) {
        Set<String> administrative = new HashSet<>();
        for (String field : ADMIN_FIELDS) {
            String value = result.path(field).asText("").strip();
            if (!value.isBlank()) administrative.add(normalize(value));
        }
        administrative.add(normalize("Việt Nam"));
        administrative.add(normalize("Vietnam"));
        var physical = new ArrayList<String>();
        String street = result.path("street").asText("").strip();
        String name = result.path("name").asText("").strip();
        if (!street.isBlank()) {
            if (!name.isBlank() && !normalize(name).equals(normalize(street)))
                addPhysical(physical, name, administrative);
            String number = result.path("housenumber").asText("").strip();
            // Structured street is physical even when its name starts with Xã/Huyện (e.g. Xã Đàn).
            String line = number.isBlank() ? street : number + " " + street;
            if (physical.stream().noneMatch(p -> normalize(p).equals(normalize(line)))) physical.add(line);
        } else {
            // Some Geoapify amenities have no structured street. Keep the physical prefix only.
            for (String part : result.path("formatted").asText("").split(",")) {
                if (!addPhysical(physical, part, administrative)) break;
            }
        }
        if (physical.isEmpty()) throw new BadRequestException("Không xác định được số nhà, đường hoặc địa điểm tại tọa độ này");
        physical.add(division.wardName());
        physical.add(division.cityName());
        physical.add("Việt Nam");
        String address = String.join(", ", physical);
        if (address.length() > 500) throw new BadRequestException("Địa chỉ vượt quá độ dài cho phép");
        return address;
    }

    private static boolean addPhysical(List<String> parts, String value, Set<String> administrative) {
        for (String fragment : value.split(",")) {
            String clean = fragment.strip();
            if (clean.isBlank()) continue;
            if (administrative.contains(normalize(clean)) || ADMIN_PREFIX.matcher(clean).matches()
                    || POSTCODE.matcher(clean).matches()) return false;
            if (parts.stream().noneMatch(p -> normalize(p).equals(normalize(clean)))) parts.add(clean);
        }
        return true;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
