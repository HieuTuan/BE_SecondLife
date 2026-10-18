package com.secondlife.secondlife.integration.shipping;

import tools.jackson.databind.JsonNode;

import java.util.Map;

public interface ShippingProvider {
    default String code() { return "LALAMOVE"; }
    default ShippingProvider forProvider(String code) { return this; }
    default JsonNode cities() { throw new com.secondlife.secondlife.exception.BadRequestException("This carrier has no city service catalogue"); }
    default JsonNode driver(String orderCode, String driverId) { throw new com.secondlife.secondlife.exception.BadRequestException("This carrier does not expose driver coordinates"); }
    JsonNode quote(Map<String, Object> payload);
    JsonNode preview(Map<String, Object> payload);
    JsonNode create(Map<String, Object> payload);
    JsonNode detail(String orderCode);
    JsonNode cancel(String orderCode);
    JsonNode returnToSender(String orderCode);
    JsonNode label(String orderCode);
}
