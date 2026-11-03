package com.secondlife.secondlife.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShippingQuoteResponse(
        UUID quoteId, String leg, BigDecimal shippingFee, BigDecimal productPrice,
        BigDecimal totalPayable, BigDecimal insuranceValue, Instant expiresAt, Instant expectedDeliveryTime,
        String provider, String serviceType, String providerQuotationId,
        @Schema(description = "Packed weight used for this quotation, in grams") int shippingWeight,
        @Schema(description = "Packed length used for this quotation, in centimeters") int shippingLength,
        @Schema(description = "Packed width used for this quotation, in centimeters") int shippingWidth,
        @Schema(description = "Packed height used for this quotation, in centimeters") int shippingHeight
) {}
