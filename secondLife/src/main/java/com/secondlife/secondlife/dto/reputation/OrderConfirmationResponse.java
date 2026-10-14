package com.secondlife.secondlife.dto.reputation;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record OrderConfirmationResponse(UUID orderId, UUID postId, String postTitle, UUID buyerId,
        UUID sellerId, OrderStatus status, String confirmationStatus, Instant paidAt,
        Instant confirmationDeadline, Instant confirmationCancelAt, Instant sellerConfirmedAt,
        @io.swagger.v3.oas.annotations.media.Schema(description="Points snapshotted at payment; applied once if the first deadline is missed") BigDecimal latePenaltyPoints,
        @io.swagger.v3.oas.annotations.media.Schema(description="Additional snapshotted points applied once on confirmation timeout cancellation") BigDecimal timeoutPenaltyPoints) {
    public static OrderConfirmationResponse from(Order o) {
        return new OrderConfirmationResponse(o.getId(),o.getPost().getId(),o.getPost().getTitle(),
            o.getBuyer().getId(),o.getSeller().getId(),o.getStatus(),confirmationStatus(o),o.getPaidAt(),
            o.getConfirmationDeadline(),o.getConfirmationCancelAt(),o.getSellerConfirmedAt(),
            o.getConfirmationLatePenalty(),o.getConfirmationTimeoutPenalty());
    }
    public static String confirmationStatus(Order o) {
        if(o.getSellerConfirmedAt()!=null) return "CONFIRMED";
        if(o.getConfirmationDeadline()==null) return "NOT_REQUIRED";
        return o.getStatus()==OrderStatus.CANCELLED?"CANCELLED":"PENDING";
    }
}
