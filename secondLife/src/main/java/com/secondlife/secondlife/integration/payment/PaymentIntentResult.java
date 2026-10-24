package com.secondlife.secondlife.integration.payment;

import com.secondlife.secondlife.enums.PaymentStatus;

import java.math.BigDecimal;

public record PaymentIntentResult(String provider, String providerIntentId,
                                  PaymentStatus status, BigDecimal amount, String currency) {
}
