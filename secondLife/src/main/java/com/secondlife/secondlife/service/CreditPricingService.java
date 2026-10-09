package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.credit.*;
import com.secondlife.secondlife.enums.CreditType;

import java.util.List;
import java.util.UUID;

public interface CreditPricingService {
    CreditPricingResponse getPricing(Integer listingQuantity, Integer valuationQuantity, Integer aiChatQuantity);
    CreditQuoteResponse quote(int listingQuantity, int valuationQuantity, int aiChatQuantity);
    List<CreditPricingRuleResponse> getAdminPrices();
    CreditPricingRuleResponse updatePrice(UUID adminId, CreditType creditType, UpdateCreditPricingRequest request);
}
