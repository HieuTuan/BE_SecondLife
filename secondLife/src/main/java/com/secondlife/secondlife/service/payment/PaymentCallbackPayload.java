package com.secondlife.secondlife.service.payment;

import java.math.BigDecimal;

/** Mock transport payload; the provider adapter verifies its signature before processing. */
public record PaymentCallbackPayload(String eventId, String providerIntentId, String providerStatus,
                                     BigDecimal amount, String currency) {
    public PaymentCallbackPayload(String eventId, String providerIntentId, String providerStatus) {
        this(eventId, providerIntentId, providerStatus, null, null);
    }
}
