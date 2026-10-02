package com.secondlife.secondlife.service.payment;

import java.util.UUID;

public interface PaymentCallbackInboxService {
    boolean recordVerifiedCallback(UUID paymentIntentId, NormalizedPaymentCallback callback);
}
