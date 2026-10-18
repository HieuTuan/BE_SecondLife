package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.response.OrderFulfillmentResponse;
import com.secondlife.secondlife.dto.response.ReportResponse;
import com.secondlife.secondlife.dto.response.ReputationHistoryResponse;
import com.secondlife.secondlife.dto.response.ReputationResponse;
import com.secondlife.secondlife.entity.Order;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface SellerReputationService {
    public static final String FORMULA_VERSION = "TRANSACTION_SUCCESS_RATE_V3";

    ReputationResponse get(UUID sellerId);

    PageResponse<ReputationHistoryResponse> history(UUID sellerId, Pageable page);

    void requireSellingAllowed(UUID sellerId);

    void recordCompletedSale(Order requestedOrder);

    void confirmSellerFault(ReportResponse report, UUID reviewerId, String decisionReason);

    void initializePaidOrder(Order order);

    OrderFulfillmentResponse readyForPickup(UUID sellerId, UUID orderId);

    OrderFulfillmentResponse fulfillment(UUID actorId, boolean reviewer, UUID orderId);

    void recordSellerCancellation(Order order, UUID actorId, String reason);

    void recordClosedOrder(Order order, UUID actorId, String reason, boolean sellerReturnFault);

    boolean recordConfirmationPenalty(Order order, String milestone, BigDecimal points);

    ReputationResponse liftRestriction(UUID sellerId, UUID adminId, String reason);

}
