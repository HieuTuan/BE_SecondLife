package com.secondlife.secondlife.service.payment;

public interface PaymentProvider {
    String providerName();
    PaymentIntentResult createIntent(PaymentIntentRequest request);
    NormalizedPaymentCallback normalizeVerifiedCallback(PaymentCallbackPayload payload);
    NormalizedPaymentCallback verifyCallback(String rawBody, String signature);
}
