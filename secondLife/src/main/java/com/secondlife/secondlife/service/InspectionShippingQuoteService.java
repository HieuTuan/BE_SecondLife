package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionReturnShippingPolicyRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionReturnShippingPolicyResponse;
import com.secondlife.secondlife.dto.response.InspectionShippingQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;

import java.util.Map;
import java.util.UUID;

public interface InspectionShippingQuoteService {
    InspectionShippingQuoteResponse quote(UUID postId, UUID centerId, UUID quoteId);
    InspectionShippingQuoteResponse quote(Post post, UUID centerId, UUID quoteId, ShippingAddress sellerPickup);
    InspectionReturnShippingPolicyResponse getReturnPolicy();
    InspectionReturnShippingPolicyResponse updateReturnPolicy(InspectionReturnShippingPolicyRequest request);
    void bind(InspectionOrder order, InspectionShippingQuoteResponse quote);
    Map<String, Object> outboundPayload(InspectionOrder order);
}
