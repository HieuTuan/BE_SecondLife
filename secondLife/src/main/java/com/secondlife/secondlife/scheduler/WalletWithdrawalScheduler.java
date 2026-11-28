package com.secondlife.secondlife.scheduler;

import com.secondlife.secondlife.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletWithdrawalScheduler {
    private final WalletService walletService;

    @Scheduled(fixedDelayString = "${app.wallet.withdrawal-expiry-interval-ms:60000}")
    public void completeExpiredWithdrawals() {
        try {
            int completed = walletService.completeExpiredWithdrawals();
            if (completed > 0) log.info("Auto-completed {} withdrawal requests after the dispute window", completed);
        } catch (RuntimeException ex) {
            log.error("Failed to complete expired withdrawal requests", ex);
        }
    }
}
