package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByPurchaseId(UUID purchaseId);
    Optional<PaymentIntent> findByProviderAndProviderIntentId(String provider, String providerIntentId);
}
