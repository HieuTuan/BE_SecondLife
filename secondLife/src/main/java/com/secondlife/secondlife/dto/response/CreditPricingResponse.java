package com.secondlife.secondlife.dto.response;

import java.util.List;

public record CreditPricingResponse(List<CreditPricingRuleResponse> prices,
                                    CreditQuoteResponse quote) {
}
