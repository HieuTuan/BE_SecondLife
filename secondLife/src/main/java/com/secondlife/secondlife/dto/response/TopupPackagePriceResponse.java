package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;

public record TopupPackagePriceResponse(
        boolean pricingAvailable, BigDecimal referencePrice, BigDecimal discountPercentage,
        BigDecimal listingUnitPrice, BigDecimal chatUnitPrice, BigDecimal valuationUnitPrice
) {
}
