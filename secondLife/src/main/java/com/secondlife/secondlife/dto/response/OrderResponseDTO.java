package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class OrderResponseDTO {
    private UUID shippingQuoteId;
    private BigDecimal shippingFee;
    private BigDecimal totalPaid;
    private Instant shippingDeliveredAt;
    private UUID id;
    private UUID postId;
    private String postTitle;
    private UUID buyerId;
    private UUID sellerId;
    private UUID negotiationId;
    private BigDecimal finalPrice;
    private OrderStatus status;
    private EscrowStatus escrowStatus;
    private Instant createdAt;
}
