package com.secondlife.secondlife.integration.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentIntentRequest(UUID purchaseId, BigDecimal amount, String currency) {
}
