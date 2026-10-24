package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.CreateCreditPurchaseRequest;
import com.secondlife.secondlife.dto.response.CreditLedgerResponse;
import com.secondlife.secondlife.dto.response.CreditPurchaseResponse;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface CreditPurchaseService {
    CreditPurchaseResponse createPurchase(UUID sellerId, CreateCreditPurchaseRequest request);
    PageResponse<CreditPurchaseResponse> getPurchases(UUID sellerId, Pageable pageable);
    PageResponse<CreditLedgerResponse> getLedger(UUID sellerId, Pageable pageable);
}
