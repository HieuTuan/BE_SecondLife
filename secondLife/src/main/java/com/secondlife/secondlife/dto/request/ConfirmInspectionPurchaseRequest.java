package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.enums.OrderPaymentMethod;
import jakarta.validation.Valid;

import java.util.UUID;

public record ConfirmInspectionPurchaseRequest(
    UUID requestId,
    UUID shippingQuoteId,
    OrderPaymentMethod paymentMethod,
    @Valid ShippingAddressRequest deliveryAddress
) {
}
