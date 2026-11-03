package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.ShippingVehicleCatalogResponse;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

public interface ShippingCatalogService {
    JsonNode cities();

    JsonNode provinces();

    ShippingVehicleCatalogResponse vehicles(BigDecimal latitude, BigDecimal longitude);

}
