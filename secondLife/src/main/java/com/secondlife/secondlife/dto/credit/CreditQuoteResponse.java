package com.secondlife.secondlife.dto.credit;

import java.math.BigDecimal;

/** All values needed for the eventual immutable purchase snapshot. */
public record CreditQuoteResponse(int listingQuantity, int valuationQuantity,
                                  BigDecimal listingUnitPrice, BigDecimal valuationUnitPrice,
                                  BigDecimal subtotal, BigDecimal finalFee, String currency) {
}
