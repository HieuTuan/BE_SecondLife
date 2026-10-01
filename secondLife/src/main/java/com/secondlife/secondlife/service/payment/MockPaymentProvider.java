package com.secondlife.secondlife.service.payment;

import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Mock adapter. The callback is trusted only after HMAC verification. */
@Component
@RequiredArgsConstructor
public class MockPaymentProvider implements PaymentProvider {
    private final Environment environment;
    private final ObjectMapper objectMapper;

    @Override
    public String providerName() {
        return "MOCK";
    }

    @Override
    public PaymentIntentResult createIntent(PaymentIntentRequest request) {
        if (request == null || request.purchaseId() == null || request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0 || request.currency() == null) {
            throw new BadRequestException("Invalid payment intent request");
        }
        return new PaymentIntentResult(providerName(), "MOCK-" + request.purchaseId(),
                PaymentStatus.PENDING, request.amount(), request.currency());
    }

    @Override
    public NormalizedPaymentCallback normalizeVerifiedCallback(PaymentCallbackPayload payload) {
        if (payload == null || payload.eventId() == null || payload.eventId().isBlank()
                || payload.eventId().length() > 150
                || payload.providerIntentId() == null || payload.providerIntentId().isBlank()
                || payload.providerIntentId().length() > 150
                || payload.providerStatus() == null) {
            throw new BadRequestException("Invalid payment callback");
        }
        PaymentStatus status = switch (payload.providerStatus().toUpperCase(Locale.ROOT)) {
            case "PENDING" -> PaymentStatus.PENDING;
            case "PAID", "SUCCESS" -> PaymentStatus.SUCCEEDED;
            case "FAILED", "CANCELLED" -> PaymentStatus.FAILED;
            default -> throw new BadRequestException("Unknown payment provider status");
        };
        return new NormalizedPaymentCallback(providerName(), payload.eventId(),
                payload.providerIntentId(), status, payload.amount(), payload.currency());
    }

    @Override
    public NormalizedPaymentCallback verifyCallback(String rawBody, String signature) {
        String secret = environment.getProperty("PAYMENT_MOCK_WEBHOOK_SECRET");
        if (secret == null || secret.length() < 32) {
            throw new ConflictException("Mock payment callback is not configured");
        }
        if (rawBody == null || rawBody.isBlank() || signature == null) {
            throw new UnauthorizedException("Invalid payment callback signature");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, actual)) {
                throw new UnauthorizedException("Invalid payment callback signature");
            }
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid payment callback signature");
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Payment callback authentication unavailable", ex);
        }
        try {
            PaymentCallbackPayload payload = objectMapper.readValue(rawBody, PaymentCallbackPayload.class);
            if (payload.amount() == null || payload.amount().compareTo(BigDecimal.ZERO) <= 0
                    || payload.currency() == null || payload.currency().isBlank()) {
                throw new BadRequestException("Invalid payment callback amount");
            }
            return normalizeVerifiedCallback(payload);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BadRequestException("Invalid payment callback body");
        }
    }
}
