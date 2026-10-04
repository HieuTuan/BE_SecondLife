package com.secondlife.secondlife.dto.shipping;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
public record ShippingQuoteResponse(UUID quoteId, String leg, BigDecimal shippingFee, BigDecimal productPrice,
        BigDecimal totalPayable, BigDecimal insuranceValue, Instant expiresAt, Instant expectedDeliveryTime) {}
