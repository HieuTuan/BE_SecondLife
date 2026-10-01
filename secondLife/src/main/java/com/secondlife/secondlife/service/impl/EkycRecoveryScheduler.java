package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.config.EkycRecoveryProperties;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.service.SellerVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ekyc.recovery.enabled", havingValue = "true")
public class EkycRecoveryScheduler {

    private final SellerVerificationRepository sellerVerificationRepository;
    private final SellerVerificationService sellerVerificationService;
    private final EkycRecoveryProperties properties;

    @Scheduled(fixedDelayString = "${app.ekyc.recovery.interval-ms}")
    public void retryDueVerifications() {
        for (UUID id : sellerVerificationRepository.findDueRecoveryIds(
                Instant.now(), properties.maxAttempts(), PageRequest.of(0, properties.batchSize()))) {
            try {
                sellerVerificationService.retryPendingVerificationSystem(id);
            } catch (RuntimeException ex) {
                log.warn("Background eKYC retry failed for verification ID {} ({})",
                        id, ex.getClass().getSimpleName());
            }
        }
    }
}
