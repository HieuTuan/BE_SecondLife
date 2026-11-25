package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.PreListingInspectionConfirmRequest;
import com.secondlife.secondlife.dto.request.PreListingInspectionQuoteRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.PreListingInspectionQuoteResponse;
import java.util.UUID;

public interface PreListingInspectionService {
    PreListingInspectionQuoteResponse quote(UUID sellerId, UUID postId, PreListingInspectionQuoteRequest request);
    InspectionOrderResponse confirm(UUID sellerId, UUID postId, PreListingInspectionConfirmRequest request);
}
