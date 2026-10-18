package com.secondlife.secondlife.service;

import com.secondlife.secondlife.integration.payment.NormalizedPaymentCallback;
import java.util.UUID;

public interface PaymentCallbackInboxService {
    boolean recordVerifiedCallback(UUID paymentIntentId, NormalizedPaymentCallback callback);
}
