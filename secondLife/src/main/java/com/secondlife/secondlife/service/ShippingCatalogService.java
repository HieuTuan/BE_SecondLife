package com.secondlife.secondlife.service;

import tools.jackson.databind.JsonNode;

public interface ShippingCatalogService {
    JsonNode cities();

    JsonNode provinces();

}
