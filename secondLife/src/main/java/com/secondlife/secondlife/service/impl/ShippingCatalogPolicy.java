package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.config.ShippingCatalogProperties;
import com.secondlife.secondlife.exception.ShippingProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.util.Locale;
import java.util.List;
import java.util.function.Predicate;

@Component @RequiredArgsConstructor
public class ShippingCatalogPolicy {
    private static final List<String> NAME_PREFIXES=List.of("thanhpho","tinh","tp");
    private final ShippingCatalogProperties properties;
    private final ObjectMapper mapper;

    public JsonNode cities(JsonNode catalogue) {
        return filter(catalogue,city -> {
            String locode=city.path("locode").asText().strip().toUpperCase(Locale.ROOT).replaceAll("\\s+"," ");
            ShippingCatalogProperties.City selected=switch(locode) {
                case "VN HAN" -> ShippingCatalogProperties.City.HANOI;
                case "VN DAD" -> ShippingCatalogProperties.City.DANANG;
                case "VN SGN" -> ShippingCatalogProperties.City.HO_CHI_MINH;
                default -> null;
            };
            // A present provider code is authoritative; a name must not override a foreign or unknown code.
            return locode.isEmpty()?allowedName(city.path("name").asText()):selected!=null && properties.getAllowedCities().contains(selected);
        });
    }
    private boolean allowedName(String name) {
        return allowedCity(name)!=null;
    }
    public ShippingCatalogProperties.City allowedCity(String name) {
        return enabled(canonicalCity(normalizeName(name)));
    }
    public ShippingCatalogProperties.City allowedAdministrativeCity(String state) {
        String normalized=normalizeName(state);
        if(normalized==null) return null;
        if(normalized.endsWith("province")) normalized=normalized.substring(0,normalized.length()-"province".length());
        var city=canonicalCity(normalized);
        // Geoapify can retain province names predating Resolution 202/2025/QH15 (July 2025).
        // Apply these aliases to geocoding states only, not to the carrier's city catalogue.
        if(city==null) city=switch(normalized) {
            case "binhduong","bariavungtau" -> ShippingCatalogProperties.City.HO_CHI_MINH;
            case "quangnam" -> ShippingCatalogProperties.City.DANANG;
            default -> null;
        };
        return enabled(city);
    }
    private String normalizeName(String name) {
        if(name==null) return null;
        String normalized=Normalizer.normalize(name,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
                .replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
        for(String prefix:NAME_PREFIXES) if(normalized.startsWith(prefix)) { normalized=normalized.substring(prefix.length()); break; }
        return normalized;
    }
    private ShippingCatalogProperties.City canonicalCity(String normalized) {
        if(normalized==null) return null;
        return switch(normalized) {
            case "hanoi" -> ShippingCatalogProperties.City.HANOI;
            case "danang" -> ShippingCatalogProperties.City.DANANG;
            case "hochiminh","hochiminhcity","hcm","hcmc" -> ShippingCatalogProperties.City.HO_CHI_MINH;
            default -> null;
        };
    }
    private ShippingCatalogProperties.City enabled(ShippingCatalogProperties.City city) {
        return city!=null && properties.getAllowedCities().contains(city)?city:null;
    }
    private JsonNode filter(JsonNode catalogue,Predicate<JsonNode> allowed) {
        if(catalogue==null || !catalogue.isArray()) throw new ShippingProviderException("Carrier returned an invalid location catalogue");
        var result=mapper.createArrayNode();
        for(var row:catalogue) if(row.isObject() && allowed.test(row)) result.add(row);
        return result;
    }
}
