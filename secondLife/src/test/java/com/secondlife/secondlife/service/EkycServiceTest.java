package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.VerificationType;
import com.secondlife.secondlife.service.ekyc.EkycProviderClient;
import com.secondlife.secondlife.service.impl.EkycServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EkycServiceTest {

    @Mock
    private EkycProviderClient ekycProviderClient;

    private EkycServiceImpl ekycService;

    @BeforeEach
    void setUp() {
        ekycService = new EkycServiceImpl(ekycProviderClient, 2);
    }

    @Test
    void verifyIdentity_WhenPass_ShouldReturnPassedWithoutRetrying() {
        EkycRequest req = new EkycRequest(VerificationType.CITIZEN_ID, "001", "f", "b", "s");
        EkycResult passResult = EkycResult.pass("MOCK", "REF1", 0.99, 0.99, 0.98);

        when(ekycProviderClient.verify(req)).thenReturn(passResult);

        EkycResult result = ekycService.verifyIdentity(req);

        assertEquals(EkycStatus.PASSED, result.status());
        verify(ekycProviderClient, times(1)).verify(req);
    }

    @Test
    void verifyIdentity_WhenFail_ShouldReturnFailedWithoutRetrying() {
        EkycRequest req = new EkycRequest(VerificationType.CITIZEN_ID, "001", "f", "b", "s");
        EkycResult failResult = EkycResult.fail(ReasonCode.DOCUMENT_EXPIRED, "MOCK", "REF1");

        when(ekycProviderClient.verify(req)).thenReturn(failResult);

        EkycResult result = ekycService.verifyIdentity(req);

        assertEquals(EkycStatus.FAILED, result.status());
        assertEquals(ReasonCode.DOCUMENT_EXPIRED, result.reasonCode());
        verify(ekycProviderClient, times(1)).verify(req);
    }

    @Test
    void verifyIdentity_WhenProviderError_ShouldRetryUpToMaxRetries() {
        EkycRequest req = new EkycRequest(VerificationType.CITIZEN_ID, "001", "f", "b", "s");
        EkycResult errResult = EkycResult.providerError(ReasonCode.PROVIDER_TIMEOUT, "MOCK", "REF1", "timeout");

        when(ekycProviderClient.verify(req)).thenReturn(errResult);

        EkycResult result = ekycService.verifyIdentity(req);

        assertEquals(EkycStatus.PROVIDER_ERROR, result.status());
        verify(ekycProviderClient, times(3)).verify(req); // initial + 2 retries
    }

    @Test
    void verifyIdentity_WhenExceptionThrown_ShouldRetryAndReturnProviderError() {
        EkycRequest req = new EkycRequest(VerificationType.CITIZEN_ID, "001", "f", "b", "s");

        when(ekycProviderClient.verify(req)).thenThrow(new RuntimeException("Connection reset"));
        when(ekycProviderClient.getProviderName()).thenReturn("MOCK");

        EkycResult result = ekycService.verifyIdentity(req);

        assertEquals(EkycStatus.PROVIDER_ERROR, result.status());
        verify(ekycProviderClient, times(3)).verify(req);
    }
}
