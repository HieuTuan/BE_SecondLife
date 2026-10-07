package com.secondlife.secondlife.dto.credit;

import java.util.List;

public record CreditPricingResponse(List<CreditPricingRuleResponse> prices,
                                    CreditQuoteResponse quote) {
}
