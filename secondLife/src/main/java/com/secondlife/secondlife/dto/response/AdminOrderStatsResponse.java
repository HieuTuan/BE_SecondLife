package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record AdminOrderStatsResponse(
    long totalOrders,
    long confirmedOrders,
    long processingOrders,
    long shippingOrders,
    long deliveredOrders,
    long completedOrders,
    long cancelledOrders,
    long disputedOrders,
    BigDecimal totalGmv,
    BigDecimal heldEscrowAmount,
    long withInspectionCount
) {}
