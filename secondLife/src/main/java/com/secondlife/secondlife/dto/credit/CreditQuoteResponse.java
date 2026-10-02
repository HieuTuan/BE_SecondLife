package com.secondlife.secondlife.dto.credit;

import java.math.BigDecimal;
import java.util.UUID;

/** All values needed for the eventual immutable purchase snapshot. */
public record CreditQuoteResponse(int listingQuantity, int valuationQuantity,
                                  BigDecimal listingUnitPrice, BigDecimal valuationUnitPrice,
                                  UUID discountTierId, int discountMinQuantity, Integer discountMaxQuantity,
                                  BigDecimal discountRate, BigDecimal subtotal,
                                  BigDecimal discountAmount, BigDecimal finalFee, String currency) {
}
