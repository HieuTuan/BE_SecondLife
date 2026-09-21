package com.secondlife.secondlife.dto.ekyc;

import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;

public record EkycResult(
        EkycStatus status,
        ReasonCode reasonCode,
        boolean documentValid,
        boolean documentExpired,
        boolean faceMatchPassed,
        boolean livenessPassed,
        Double faceMatchScore,
        Double livenessScore,
        Double documentScore,
        String providerName,
        String providerReferenceId,
        String userMessage
) {
    public static EkycResult pass(String providerName, String providerRef, Double faceMatchScore, Double livenessScore, Double docScore) {
        return new EkycResult(
                EkycStatus.PASSED,
                ReasonCode.NONE,
                true,
                false,
                true,
                true,
                faceMatchScore,
                livenessScore,
                docScore,
                providerName,
                providerRef,
                "Xác thực danh tính eKYC thành công"
        );
    }

    public static EkycResult fail(ReasonCode reasonCode, String providerName, String providerRef) {
        return new EkycResult(
                EkycStatus.FAILED,
                reasonCode,
                false,
                reasonCode == ReasonCode.DOCUMENT_EXPIRED,
                false,
                false,
                null,
                null,
                null,
                providerName,
                providerRef,
                reasonCode.getDescription()
        );
    }

    public static EkycResult uncertain(ReasonCode reasonCode, String providerName, String providerRef, Double faceMatchScore, Double livenessScore, Double docScore) {
        return new EkycResult(
                EkycStatus.UNCERTAIN,
                reasonCode,
                false,
                false,
                false,
                false,
                faceMatchScore,
                livenessScore,
                docScore,
                providerName,
                providerRef,
                reasonCode.getDescription()
        );
    }

    public static EkycResult providerError(ReasonCode reasonCode, String providerName, String providerRef, String message) {
        return new EkycResult(
                EkycStatus.PROVIDER_ERROR,
                reasonCode,
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                providerName,
                providerRef,
                message
        );
    }
}
