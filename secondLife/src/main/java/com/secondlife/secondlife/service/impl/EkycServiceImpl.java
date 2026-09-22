package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.service.EkycService;
import com.secondlife.secondlife.service.ekyc.EkycProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EkycServiceImpl implements EkycService {

    private final EkycProviderClient ekycProviderClient;
    private final int maxRetries;

    public EkycServiceImpl(EkycProviderClient ekycProviderClient,
                           @Value("${app.ekyc.max-retries}") int maxRetries) {
        this.ekycProviderClient = ekycProviderClient;
        this.maxRetries = maxRetries;
    }

    @Override
    public EkycResult verifyIdentity(EkycRequest request) {
        log.info("Starting eKYC verification process with provider: {}", ekycProviderClient.getProviderName());

        int attempts = 0;
        EkycResult lastResult = null;

        while (attempts <= maxRetries) {
            attempts++;
            try {
                lastResult = ekycProviderClient.verify(request);

                // If result is not a temporary provider error, return immediately
                if (lastResult.status() != EkycStatus.PROVIDER_ERROR) {
                    log.info("eKYC verification finished on attempt {} with status: {}, reason: {}",
                            attempts, lastResult.status(), lastResult.reasonCode());
                    return lastResult;
                }

                log.warn("eKYC provider reported recoverable error on attempt {}/{}: {}",
                        attempts, maxRetries + 1, lastResult.reasonCode());

            } catch (Exception ex) {
                log.error("Exception during eKYC provider call on attempt {}/{}: {}",
                        attempts, maxRetries + 1, ex.getMessage());
                lastResult = EkycResult.providerError(
                        ReasonCode.PROVIDER_UNAVAILABLE,
                        ekycProviderClient.getProviderName(),
                        null,
                        "Lỗi hệ thống khi kết nối đến cổng eKYC: " + ex.getMessage()
                );
            }
        }

        log.warn("eKYC verification exhausted all {} retry attempts. Final status: {}",
                attempts, lastResult != null ? lastResult.status() : EkycStatus.PROVIDER_ERROR);

        return lastResult != null ? lastResult : EkycResult.providerError(
                ReasonCode.PROVIDER_TIMEOUT,
                ekycProviderClient.getProviderName(),
                null,
                "Hệ thống eKYC quá thời gian phản hồi"
        );
    }
}
