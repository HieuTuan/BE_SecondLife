package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.PaymentCallbackEvent;
import com.secondlife.secondlife.integration.payment.NormalizedPaymentCallback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.Optional;

public interface PaymentCallbackEventRepository extends JpaRepository<PaymentCallbackEvent, UUID> {
    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
    Optional<PaymentCallbackEvent> findByProviderAndProviderEventId(String provider, String providerEventId);

    /** Atomic deduplication works under concurrent callbacks without a failed transaction. */
    @Transactional
    @Query(value = """
            WITH inserted AS (
                INSERT INTO payment_callback_events
                    (payment_intent_id, provider, provider_event_id, normalized_status)
                VALUES (:paymentIntentId, :#{#callback.provider()}, :#{#callback.eventId()},
                    :#{#callback.status().name()})
                ON CONFLICT (provider, provider_event_id) DO NOTHING
                RETURNING 1
            )
            SELECT EXISTS(SELECT 1 FROM inserted)
            """, nativeQuery = true)
    boolean insertIfAbsent(@Param("paymentIntentId") UUID paymentIntentId,
                           @Param("callback") NormalizedPaymentCallback callback);
}
