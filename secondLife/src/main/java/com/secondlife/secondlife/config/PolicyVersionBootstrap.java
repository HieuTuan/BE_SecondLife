package com.secondlife.secondlife.config;

import com.secondlife.secondlife.service.PolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.policy.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class PolicyVersionBootstrap implements ApplicationRunner {

    private final PolicyService policyService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            policyService.ensureInitialTermsSeeded();
        } catch (Exception ex) {
            log.error("Failed to ensure initial Terms of Service version is seeded", ex);
            throw ex;
        }
    }
}
