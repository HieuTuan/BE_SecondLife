package com.secondlife.secondlife.dto.credit;

import java.util.List;

public record CreditPricingResponse(List<CreditPricingRuleResponse> prices,
                                    List<CreditDiscountTierResponse> discountTiers,
                                    CreditQuoteResponse quote) {
}
