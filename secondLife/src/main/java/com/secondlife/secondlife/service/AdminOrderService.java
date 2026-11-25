package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.AdminOrderResponse;
import com.secondlife.secondlife.dto.response.AdminOrderStatsResponse;
import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AdminOrderService {
    Page<AdminOrderResponse> getAdminOrders(
        OrderStatus status,
        EscrowStatus escrowStatus,
        String search,
        Boolean hasInspection,
        Pageable pageable
    );

    AdminOrderResponse getAdminOrderById(UUID id);

    AdminOrderStatsResponse getAdminOrderStats();
}
