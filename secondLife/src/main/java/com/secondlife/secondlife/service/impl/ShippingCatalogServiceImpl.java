package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.integration.shipping.ShippingProvider;

import com.secondlife.secondlife.service.ShippingCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
@Service("shippingCatalogService")
@RequiredArgsConstructor
public class ShippingCatalogServiceImpl implements ShippingCatalogService {
    private final ShippingProvider provider;
    private final ShippingCatalogPolicy policy;
    public JsonNode cities() { return policy.cities(provider.cities()); }
    public JsonNode provinces() { return cities(); }
}
