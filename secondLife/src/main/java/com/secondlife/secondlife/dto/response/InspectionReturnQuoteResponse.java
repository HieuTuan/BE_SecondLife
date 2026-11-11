package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import java.math.BigDecimal;

public record InspectionReturnQuoteResponse(
        ShippingAddress from,
        ShippingAddress to,
        BigDecimal shippingFee,
        BigDecimal reserveFee,
        String carrier,
        String serviceType,
        String expiresAt
) {}
