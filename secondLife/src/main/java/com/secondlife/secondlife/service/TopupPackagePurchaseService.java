package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.TopupPackagePurchaseRequest;
import com.secondlife.secondlife.dto.response.TopupPackagePurchaseResponse;

import java.util.UUID;

public interface TopupPackagePurchaseService {
    TopupPackagePurchaseResponse purchase(UUID userId, UUID packageId, TopupPackagePurchaseRequest request);
    PageResponse<TopupPackagePurchaseResponse> getMyPurchases(UUID userId, int page, int size);
    TopupPackagePurchaseResponse getMyPurchase(UUID userId, UUID purchaseId);
}
