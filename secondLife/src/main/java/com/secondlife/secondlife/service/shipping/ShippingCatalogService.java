package com.secondlife.secondlife.service.shipping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
@Service @RequiredArgsConstructor
public class ShippingCatalogService {
    private final ShippingProvider provider;
    private final ShippingCatalogPolicy policy;
    public JsonNode cities() { return policy.cities(provider.cities()); }
    public JsonNode provinces() { return cities(); }
}
