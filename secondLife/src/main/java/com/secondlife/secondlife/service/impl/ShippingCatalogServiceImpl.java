package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.ShippingVehicleCatalogResponse;
import com.secondlife.secondlife.integration.shipping.LalamoveVehicleCatalog;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.service.ShippingCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

@Service("shippingCatalogService")
@RequiredArgsConstructor
public class ShippingCatalogServiceImpl implements ShippingCatalogService {
    private final ShippingProvider provider;
    private final ShippingCatalogPolicy policy;

    @Override
    public JsonNode cities() {
        return policy.cities(provider.cities());
    }

    @Override
    public JsonNode provinces() {
        return cities();
    }

    @Override
    public ShippingVehicleCatalogResponse vehicles(BigDecimal latitude, BigDecimal longitude) {
        String pickupLocode = LalamoveVehicleCatalog.pickupCityLocode(latitude, longitude);
        return LalamoveVehicleCatalog.normalize(cities(), pickupLocode);
    }
}
