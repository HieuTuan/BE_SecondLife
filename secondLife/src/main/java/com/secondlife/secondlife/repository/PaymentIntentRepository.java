package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.PaymentIntent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByPurchaseId(UUID purchaseId);
    List<PaymentIntent> findByPurchaseIdIn(Collection<UUID> purchaseIds);
    Optional<PaymentIntent> findByProviderAndProviderIntentId(String provider, String providerIntentId);
}
