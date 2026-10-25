package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.response.ReturnShippingFeeResponse;
import com.secondlife.secondlife.dto.response.SellerReturnShippingDebtResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.UserReport;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReturnShippingFeeService {
    void recordForRefund(Order order, Shipment shipment, UserReport report, UUID actorId);

    void collectPendingForRefund(Order order, Shipment shipment, UserReport report, UUID actorId);

    ReturnShippingFeeResponse get(UUID actorId, boolean reviewer, UUID shipmentId);

    PageResponse<ReturnShippingFeeResponse> mine(UUID sellerId, boolean outstandingOnly, Pageable pageable);

    SellerReturnShippingDebtResponse debt(UUID sellerId);
}
