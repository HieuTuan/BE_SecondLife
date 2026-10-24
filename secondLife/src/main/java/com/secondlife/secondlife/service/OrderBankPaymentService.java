package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SePayWebhookDTO;
import com.secondlife.secondlife.dto.response.OrderBankPaymentResponse;
import com.secondlife.secondlife.entity.Order;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface OrderBankPaymentService {
    void begin(Order order);

    void receive(UUID orderId, SePayWebhookDTO event, String code);

    Order cancel(UUID actorId, UUID orderId);

    void expire(UUID orderId);

    OrderBankPaymentResponse get(UUID buyerId, UUID orderId);

    OrderBankPaymentResponse details(Order order);

    Map<UUID, OrderBankPaymentResponse> detailsForOrders(List<Order> orders);

}
