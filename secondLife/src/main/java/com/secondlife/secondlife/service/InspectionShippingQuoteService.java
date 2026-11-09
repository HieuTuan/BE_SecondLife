package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionReturnShippingPolicyRequest;
import com.secondlife.secondlife.dto.response.InspectionReturnShippingPolicyResponse;
import com.secondlife.secondlife.dto.response.InspectionShippingQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;

import java.util.Map;
import java.util.UUID;

public interface InspectionShippingQuoteService {
    InspectionShippingQuoteResponse quote(UUID postId, UUID centerId, UUID quoteId);
    InspectionReturnShippingPolicyResponse getReturnPolicy();
    InspectionReturnShippingPolicyResponse updateReturnPolicy(InspectionReturnShippingPolicyRequest request);
    void bind(InspectionOrder order, InspectionShippingQuoteResponse quote);
    Map<String, Object> outboundPayload(InspectionOrder order);
}
