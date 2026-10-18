package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingParcel;
import com.secondlife.secondlife.dto.request.ShippingQuoteRequest;
import com.secondlife.secondlife.dto.response.ShippingQuoteResponse;
import com.secondlife.secondlife.entity.ShippingQuote;
import java.util.UUID;
import java.math.BigDecimal;

public interface ShippingQuoteService {
    ShippingAddress savePickup(UUID userId, ShippingAddress address);

    ShippingAddress pickup(UUID userId);

    ShippingParcel saveParcel(UUID sellerId, UUID postId, ShippingParcel parcel);

    ShippingQuoteResponse quote(UUID buyerId, ShippingQuoteRequest request);

    ShippingQuote consume(UUID buyerId, UUID postId, UUID quoteId, UUID orderId, BigDecimal price);

}
