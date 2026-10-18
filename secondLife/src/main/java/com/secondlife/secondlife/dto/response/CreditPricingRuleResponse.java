package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.CreditType;

import java.math.BigDecimal;

public record CreditPricingRuleResponse(CreditType creditType, BigDecimal unitPrice,
                                        String currency, boolean active) {
}
