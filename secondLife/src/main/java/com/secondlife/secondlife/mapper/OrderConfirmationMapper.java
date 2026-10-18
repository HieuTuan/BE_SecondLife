package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.OrderConfirmationResponse;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.OrderStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class OrderConfirmationMapper {
    private final Delegate delegate;

    public OrderConfirmationMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public OrderConfirmationResponse toResponse(Order order) {
        return delegate.toResponse(order);
    }

    public static String confirmationStatus(Order order) {
        if (order.getSellerConfirmedAt() != null) return "CONFIRMED";
        if (order.getConfirmationDeadline() == null) return "NOT_REQUIRED";
        return order.getStatus() == OrderStatus.CANCELLED ? "CANCELLED" : "PENDING";
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "orderId", source = "id")
        @Mapping(target = "postId", source = "post.id")
        @Mapping(target = "postTitle", source = "post.title")
        @Mapping(target = "buyerId", source = "buyer.id")
        @Mapping(target = "sellerId", source = "seller.id")
        @Mapping(target = "confirmationStatus", expression = "java(confirmationStatus(order))")
        @Mapping(target = "latePenaltyPoints", source = "confirmationLatePenalty")
        @Mapping(target = "timeoutPenaltyPoints", source = "confirmationTimeoutPenalty")
        OrderConfirmationResponse toResponse(Order order);

        default String confirmationStatus(Order order) {
            return OrderConfirmationMapper.confirmationStatus(order);
        }
    }
}
