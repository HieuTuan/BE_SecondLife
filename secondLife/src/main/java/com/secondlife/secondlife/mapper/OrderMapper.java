package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.OrderBankPaymentResponse;
import com.secondlife.secondlife.dto.response.OrderFulfillmentResponse;
import com.secondlife.secondlife.dto.response.OrderResponseDTO;
import com.secondlife.secondlife.entity.DepositRequest;
import com.secondlife.secondlife.entity.Order;
import com.secondlife.secondlife.enums.OrderPaymentMethod;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class OrderMapper {
    private final Delegate delegate;

    public OrderMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public OrderResponseDTO toResponse(Order order, OrderBankPaymentResponse payment, BigDecimal totalAmount) {
        return delegate.toResponse(order, payment, totalAmount);
    }

    public OrderFulfillmentResponse toFulfillmentResponse(Order order) {
        return delegate.toFulfillmentResponse(order);
    }

    public OrderBankPaymentResponse toBankPaymentResponse(Order order, DepositRequest deposit, String status, BigDecimal remainingAmount, String bankAccountName, String bankAccountNumber, String bankName) {
        return delegate.toBankPaymentResponse(order, deposit, status, remainingAmount, bankAccountName, bankAccountNumber, bankName);
    }

    @Mapper(config = MapStructConfiguration.class, imports = {OrderPaymentMethod.class, BigDecimal.class})
    public interface Delegate {
        @Mapping(target = "paymentMethod", source = "order.paymentMethod")
        @Mapping(target = "shippingQuoteId", source = "order.shippingQuoteId")
        @Mapping(target = "shippingFee", source = "order.shippingFee")
        @Mapping(target = "shippingDeliveredAt", source = "order.shippingDeliveredAt")
        @Mapping(target = "id", source = "order.id")
        @Mapping(target = "finalPrice", source = "order.finalPrice")
        @Mapping(target = "status", source = "order.status")
        @Mapping(target = "escrowStatus", source = "order.escrowStatus")
        @Mapping(target = "createdAt", source = "order.createdAt")
        @Mapping(target = "paidAt", source = "order.paidAt")
        @Mapping(target = "sellerConfirmedAt", source = "order.sellerConfirmedAt")
        @Mapping(target = "confirmationDeadline", source = "order.confirmationDeadline")
        @Mapping(target = "confirmationCancelAt", source = "order.confirmationCancelAt")
        @Mapping(target = "handoverDeadline", source = "order.handoverDeadline")
        @Mapping(target = "sellerReadyAt", source = "order.sellerReadyAt")
        @Mapping(target = "carrierPickedUpAt", source = "order.carrierPickedUpAt")
        @Mapping(target = "cancellationReasonCode", source = "order.cancellationReasonCode")
        @Mapping(target = "postId", source = "order.post.id")
        @Mapping(target = "postTitle", source = "order.post.title")
        @Mapping(target = "buyerId", source = "order.buyer.id")
        @Mapping(target = "sellerId", source = "order.seller.id")
        @Mapping(target = "negotiationId", source = "order.negotiation.id")
        @Mapping(target = "payment", source = "payment")
        @Mapping(target = "totalAmount", source = "totalAmount")
        @Mapping(target = "totalPaid", expression = "java(order.getPaymentMethod() == OrderPaymentMethod.BANK_TRANSFER && order.getPaidAt() == null ? BigDecimal.ZERO : totalAmount)")
        @Mapping(target = "confirmationStatus", expression = "java(OrderConfirmationMapper.confirmationStatus(order))")
        OrderResponseDTO toResponse(Order order, OrderBankPaymentResponse payment, BigDecimal totalAmount);

        @Mapping(target = "orderId", source = "id")
        @Mapping(target = "lateHandoverCandidate", expression = "java(order.getHandoverDeadline() != null && order.getCarrierPickedUpAt() != null && order.getCarrierPickedUpAt().isAfter(order.getHandoverDeadline()))")
        OrderFulfillmentResponse toFulfillmentResponse(Order order);

        @Mapping(target = "orderId", source = "order.id")
        @Mapping(target = "status", source = "status")
        @Mapping(target = "orderStatus", source = "order.status")
        @Mapping(target = "escrowStatus", source = "order.escrowStatus")
        @Mapping(target = "totalAmount", source = "deposit.amount")
        @Mapping(target = "receivedAmount", source = "deposit.receivedAmount")
        @Mapping(target = "remainingAmount", source = "remainingAmount")
        @Mapping(target = "walletCreditedAmount", source = "deposit.walletCreditedAmount")
        @Mapping(target = "transferContent", source = "deposit.code")
        @Mapping(target = "bankAccountName", source = "bankAccountName")
        @Mapping(target = "bankAccountNumber", source = "bankAccountNumber")
        @Mapping(target = "bankName", source = "bankName")
        @Mapping(target = "expiresAt", source = "deposit.expiresAt")
        @Mapping(target = "paidAt", source = "order.paidAt")
        OrderBankPaymentResponse toBankPaymentResponse(Order order, DepositRequest deposit, String status,
                BigDecimal remainingAmount, String bankAccountName, String bankAccountNumber, String bankName);
    }
}
