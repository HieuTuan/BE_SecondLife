package com.secondlife.secondlife.service.ekyc.impl;

import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.service.ekyc.EkycProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "MOCK")
public class MockEkycProviderClient implements EkycProviderClient {

    private static final String PROVIDER_NAME = "SECONDLIFE_MOCK_EKYC";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public EkycResult verify(EkycRequest request) {
        String docNumber = request.documentNumber() != null ? request.documentNumber().trim() : "";
        String refId = "REF-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("Executing mock eKYC verification for documentType: {}, ref: {}",
                request.documentType(), refId);

        // 1. Hard eKYC Failures
        if (docNumber.contains("FAIL_EXPIRED") || docNumber.endsWith("9901")) {
            return EkycResult.fail(ReasonCode.DOCUMENT_EXPIRED, PROVIDER_NAME, refId);
        }
        if (docNumber.contains("FAIL_FAKE") || docNumber.endsWith("9902")) {
            return EkycResult.fail(ReasonCode.DOCUMENT_SUSPECTED_FAKE, PROVIDER_NAME, refId);
        }
        if (docNumber.contains("FAIL_FACE") || docNumber.endsWith("9903")) {
            return EkycResult.fail(ReasonCode.FACE_MISMATCH, PROVIDER_NAME, refId);
        }
        if (docNumber.contains("FAIL_LIVENESS") || docNumber.endsWith("9904")) {
            return EkycResult.fail(ReasonCode.LIVENESS_FAILED, PROVIDER_NAME, refId);
        }
        if (docNumber.contains("FAIL_UNSUPPORTED") || docNumber.endsWith("9905")) {
            return EkycResult.fail(ReasonCode.UNSUPPORTED_DOCUMENT, PROVIDER_NAME, refId);
        }

        // 2. User-Fixable Uncertain Cases
        if (docNumber.contains("UNCERTAIN_BLURRY") || docNumber.endsWith("8801")) {
            return EkycResult.uncertain(ReasonCode.IMAGE_TOO_BLURRY, PROVIDER_NAME, refId, 0.40, 0.50, 0.35);
        }
        if (docNumber.contains("UNCERTAIN_GLARE") || docNumber.endsWith("8802")) {
            return EkycResult.uncertain(ReasonCode.IMAGE_GLARE, PROVIDER_NAME, refId, 0.45, 0.60, 0.40);
        }
        if (docNumber.contains("UNCERTAIN_NOT_VISIBLE") || docNumber.endsWith("8805")) {
            return EkycResult.uncertain(ReasonCode.DOCUMENT_NOT_FULLY_VISIBLE, PROVIDER_NAME, refId, 0.50, 0.50, 0.30);
        }

        // 3. Non-Fixable Uncertain Cases (Requires Admin Review)
        if (docNumber.contains("UNCERTAIN_BORDERLINE") || docNumber.endsWith("8803")) {
            return EkycResult.uncertain(ReasonCode.FACE_MATCH_BORDERLINE, PROVIDER_NAME, refId, 0.72, 0.85, 0.88);
        }
        if (docNumber.contains("UNCERTAIN_INCONSISTENT") || docNumber.endsWith("8804")) {
            return EkycResult.uncertain(ReasonCode.DOCUMENT_DATA_INCONSISTENCY, PROVIDER_NAME, refId, 0.90, 0.92, 0.65);
        }

        // 4. Provider Error Cases
        if (docNumber.contains("ERR_TIMEOUT") || docNumber.endsWith("7701")) {
            return EkycResult.providerError(ReasonCode.PROVIDER_TIMEOUT, PROVIDER_NAME, refId, "Cổng eKYC quá thời gian phản hồi (timeout)");
        }
        if (docNumber.contains("ERR_UNAVAILABLE") || docNumber.endsWith("7702")) {
            return EkycResult.providerError(ReasonCode.PROVIDER_UNAVAILABLE, PROVIDER_NAME, refId, "Cổng eKYC tạm thời ngắt kết nối (service unavailable)");
        }

        // Default: PASS with high confidence
        return EkycResult.pass(PROVIDER_NAME, refId, 0.98, 0.99, 0.96);
    }
}
