package com.secondlife.secondlife.service.payment;

import java.math.BigDecimal;

public record PaymentIntentResult(String provider, String providerIntentId,
                                  PaymentStatus status, BigDecimal amount, String currency) {
}
