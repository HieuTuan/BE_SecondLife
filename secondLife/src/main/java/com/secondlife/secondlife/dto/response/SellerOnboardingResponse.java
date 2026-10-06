package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import java.time.Instant;

public record SellerOnboardingResponse(String shopName, ShippingAddress pickupAddress, String email,
        String phone, boolean emailVerified, boolean canStartEkyc, String nextStep, Instant emailVerifiedAt,
        Integer provinceId, Integer wardId) {}
