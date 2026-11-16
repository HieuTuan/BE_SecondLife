package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.entity.Post;

import java.math.BigDecimal;
import java.util.UUID;

public interface InspectionLocationService {
    ShippingAddress sellerPickup(Post post);

    HubSelection selectHub(ShippingAddress sellerPickup, UUID requestedCenterId);

    record HubSelection(UUID id, String name, BigDecimal distanceKm) {
    }
}
