package com.secondlife.secondlife.integration.payment;

public interface PaymentProvider {
    String providerName();
    PaymentIntentResult createIntent(PaymentIntentRequest request);
    NormalizedPaymentCallback normalizeVerifiedCallback(PaymentCallbackPayload payload);
    NormalizedPaymentCallback verifyCallback(String rawBody, String signature);
}
