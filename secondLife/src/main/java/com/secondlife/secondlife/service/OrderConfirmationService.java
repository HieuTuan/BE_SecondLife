package com.secondlife.secondlife.service;

import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.Page;

import com.secondlife.secondlife.dto.response.OrderConfirmationResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.OrderStatus;
import java.time.*;
import java.util.UUID;

public interface OrderConfirmationService {
    Instant now();

    OrderConfirmationResponse confirm(UUID sellerId, UUID orderId);

    OrderConfirmationResponse get(UUID actor, boolean reviewer, UUID id);

    Page<OrderConfirmationResponse> queue(OrderStatus status, Pageable page);

    boolean processIfDue(UUID id);

    boolean recordLateCancellationIfDue(Order o, Instant cancelledAt);

}
