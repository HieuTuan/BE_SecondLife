package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.PaymentCallbackEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface PaymentCallbackEventRepository extends JpaRepository<PaymentCallbackEvent, UUID> {
    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);
    Optional<PaymentCallbackEvent> findByProviderAndProviderEventId(String provider, String providerEventId);
}
