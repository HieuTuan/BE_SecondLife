package com.secondlife.secondlife.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnptEkycPropertiesTest {
    @Test
    void missingIndependentTokenIdAndKeyStopVnptStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(VnptEkycConfig.class)
                .withPropertyValues(
                        "app.ekyc.provider=VNPT",
                        "app.vnpt.base-url=https://api.idg.vnpt.vn",
                        "app.vnpt.client-id=dummy-client-id",
                        "app.vnpt.client-secret=dummy-client-secret",
                        "app.vnpt.mac-address=TEST1",
                        "app.vnpt.timeout-ms=1000")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    String message = context.getStartupFailure().toString();
                    assertTrue(message.contains("app.vnpt") || message.contains("VnptEkycProperties"));
                });
    }
}
