package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.OrderRequestDTO;
import com.secondlife.secondlife.dto.response.OrderResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface OrderService {
    OrderResponseDTO createOrder(UUID buyerId, OrderRequestDTO requestDTO);
    OrderResponseDTO markAsShipped(UUID sellerId, UUID orderId);
    OrderResponseDTO confirmDelivery(UUID buyerId, UUID orderId); // this releases the escrow
    OrderResponseDTO cancelOrder(UUID userId, UUID orderId); // cancels and refunds
    Page<OrderResponseDTO> getBuyerOrders(UUID buyerId, Pageable pageable);
    Page<OrderResponseDTO> getSellerOrders(UUID sellerId, Pageable pageable);
}
