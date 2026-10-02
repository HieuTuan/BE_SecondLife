package com.secondlife.secondlife.service.payment;

import java.math.BigDecimal;

public record NormalizedPaymentCallback(String provider, String eventId,
                                        String providerIntentId, PaymentStatus status,
                                        BigDecimal amount, String currency) {
    public NormalizedPaymentCallback(String provider, String eventId,
                                     String providerIntentId, PaymentStatus status) {
        this(provider, eventId, providerIntentId, status, null, null);
    }
}
