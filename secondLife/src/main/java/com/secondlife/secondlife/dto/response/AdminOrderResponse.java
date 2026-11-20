package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderPaymentMethod;
import com.secondlife.secondlife.enums.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record AdminOrderResponse(
    UUID id,
    UUID postId,
    String postTitle,
    String postImageUrl,
    BigDecimal postPrice,
    UUID buyerId,
    String buyerName,
    String buyerEmail,
    String buyerPhone,
    UUID sellerId,
    String sellerName,
    String sellerEmail,
    String sellerPhone,
    BigDecimal finalPrice,
    BigDecimal shippingFee,
    BigDecimal totalAmount,
    OrderStatus status,
    EscrowStatus escrowStatus,
    OrderPaymentMethod paymentMethod,
    String deliveryAddress,
    String cancellationReasonCode,
    boolean hasInspection,
    UUID inspectionOrderId,
    String inspectionStatus,
    Instant createdAt,
    Instant paidAt,
    Instant sellerConfirmedAt,
    Instant shippingDeliveredAt,
    Instant updatedAt
) {}
