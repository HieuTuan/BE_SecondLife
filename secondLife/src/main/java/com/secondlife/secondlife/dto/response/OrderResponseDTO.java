package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.EscrowStatus;
import com.secondlife.secondlife.enums.OrderPaymentMethod;
import com.secondlife.secondlife.enums.OrderStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record OrderResponseDTO(
    OrderPaymentMethod paymentMethod,
    BigDecimal totalAmount,
    OrderBankPaymentResponse payment,
    UUID shippingQuoteId,
    BigDecimal shippingFee,
    BigDecimal totalPaid,
    Instant shippingDeliveredAt,
    UUID id,
    UUID postId,
    String postTitle,
    UUID buyerId,
    UUID sellerId,
    UUID negotiationId,
    BigDecimal finalPrice,
    OrderStatus status,
    EscrowStatus escrowStatus,
    Instant createdAt,
    Instant paidAt,
    Instant sellerConfirmedAt,
    Instant confirmationDeadline,
    Instant confirmationCancelAt,
    String confirmationStatus,
    Instant handoverDeadline,
    Instant sellerReadyAt,
    Instant carrierPickedUpAt,
    String cancellationReasonCode
) {
}
