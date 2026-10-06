package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.dto.request.SellerPickupAddressRequest;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class GhnPickupAddressResolver {
    private final ShippingProvider provider;

    public ShippingAddress resolve(SellerPickupAddressRequest request) {
        if (request.provinceId() == null || request.provinceId() <= 0
                || request.wardId() == null || request.wardId() <= 0) {
            throw new BadRequestException("Select a GHN province and ward for the pickup address");
        }
        JsonNode province = activeLocation(provider.provinces(), request.provinceId(), "province");
        JsonNode ward = activeLocation(provider.wards(request.provinceId()), request.wardId(), "ward");
        if (!ward.path("parent_id").isIntegralNumber() || ward.path("parent_id").asInt() != request.provinceId()) {
            throw new BadRequestException("Selected GHN ward does not belong to the selected province");
        }
        return new ShippingAddress(request.name().strip(), request.phone().strip(), request.address().strip(),
                province.path("name").asText(), null, ward.path("name").asText(), null, null, true);
    }

    private JsonNode activeLocation(JsonNode locations, int id, String type) {
        if (locations == null || !locations.isArray()) {
            throw new ShippingProviderException("GHN returned an invalid address catalogue");
        }
        for (JsonNode location : locations) {
            if (location.path("_id").isIntegralNumber() && location.path("_id").asLong() == id
                    && location.path("status").asInt() == 1 && type.equals(location.path("type").asText())) {
                if (!location.path("name").isTextual() || location.path("name").asText().isBlank()
                        || location.path("name").asText().length() > 100) {
                    throw new ShippingProviderException("GHN returned an invalid address catalogue");
                }
                return location;
            }
        }
        throw new BadRequestException("Selected GHN " + type + " is not available");
    }
}
