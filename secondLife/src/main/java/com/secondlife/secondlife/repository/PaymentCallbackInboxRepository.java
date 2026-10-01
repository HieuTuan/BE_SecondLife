package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.service.payment.NormalizedPaymentCallback;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PaymentCallbackInboxRepository {
    private final JdbcTemplate jdbcTemplate;

    /** Atomic deduplication works under concurrent callbacks without a failed transaction. */
    public boolean insertIfAbsent(UUID paymentIntentId, NormalizedPaymentCallback callback) {
        return jdbcTemplate.update("""
                INSERT INTO payment_callback_events
                    (payment_intent_id, provider, provider_event_id, normalized_status)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (provider, provider_event_id) DO NOTHING
                """, paymentIntentId, callback.provider(), callback.eventId(), callback.status().name()) == 1;
    }
}
