package com.secondlife.secondlife.service.shipping;

import tools.jackson.databind.JsonNode;

import java.util.Map;

public interface ShippingProvider {
    JsonNode provinces();
    JsonNode legacyProvinces();
    JsonNode wards(int provinceId);
    JsonNode districts(int provinceId);
    JsonNode legacyWards(int districtId);
    JsonNode services(int fromDistrictId, int toDistrictId);
    JsonNode quote(Map<String, Object> payload);
    JsonNode preview(Map<String, Object> payload);
    JsonNode leadtime(Map<String, Object> payload);
    JsonNode create(Map<String, Object> payload);
    JsonNode detail(String orderCode);
    JsonNode cancel(String orderCode);
    JsonNode returnToSender(String orderCode);
    JsonNode label(String orderCode);
}
