package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.service.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.token-cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class TokenCleanupScheduler {

    private final TokenService tokenService;

    @Scheduled(
            fixedDelayString = "${app.auth.token-cleanup.interval-ms:86400000}",
            initialDelayString = "${app.auth.token-cleanup.initial-delay-ms:60000}"
    )
    public void cleanupExpiredTokens() {
        try {
            log.info("Running scheduled cleanup for expired and revoked tokens...");
            int purged = tokenService.cleanupExpiredAndRevokedTokens();
            log.info("Scheduled token cleanup finished. Total removed: {}", purged);
        } catch (Exception ex) {
            log.error("Failed to run scheduled token cleanup: {}", ex.getMessage(), ex);
        }
    }
}
