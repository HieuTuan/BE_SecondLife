package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import com.secondlife.secondlife.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptEkycOrchestrator {
    private final VnptTokenService tokenService;
    private final VnptFileClient fileClient;
    private final VnptOcrClient ocrClient;
    private final VnptCardLivenessClient cardClient;
    private final VnptFaceLivenessClient faceClient;
    private final VnptMaskFaceClient maskClient;
    private final VnptFaceCompareClient compareClient;
    private final EkycVerificationPolicy policy;

    public VnptResults.Verification verify(byte[] frontImage, byte[] backImage, byte[] selfieImage,
                                           String clientSession, String token, int documentType) {
        if (clientSession == null || clientSession.isBlank() || token == null || token.isBlank()) {
            throw new BadRequestException("clientSession and token are required for VNPT eKYC");
        }
        if (clientSession.length() > 255 || token.length() > 255) {
            throw new BadRequestException("clientSession and token must be at most 255 characters");
        }
        fileClient.validateImage(frontImage);
        fileClient.validateImage(backImage);
        fileClient.validateImage(selfieImage);
        String session = normalizeClientSession(clientSession);
        String safeToken = token.trim();
        // The three hashes are reused by every downstream check in this request.
        tokenService.getToken();
        String frontHash = fileClient.upload(frontImage, "front");
        String backHash = fileClient.upload(backImage, "back");
        String selfieHash = fileClient.upload(selfieImage, "selfie");
        var ocr = ocrClient.analyze(frontHash, backHash, session, safeToken, documentType);
        var card = cardClient.check(frontHash, session, safeToken);
        var face = faceClient.check(selfieHash, session, safeToken);
        var mask = maskClient.check(selfieHash, session, safeToken);
        var compare = compareClient.compare(frontHash, selfieHash, session, safeToken);
        var decision = policy.decide(ocr, card, face, mask, compare);
        return new VnptResults.Verification(UUID.randomUUID(), decision.verified(), decision.reasonCode(),
                ocr, card, face, mask, compare);
    }

    public static String normalizeClientSession(String session) {
        if (session == null || session.isBlank()) {
            return "ANDROID_Web_1.0_Device_1.0.0_web_" + System.currentTimeMillis();
        }
        String trimmed = session.trim();
        if (trimmed.startsWith("IOS_") || trimmed.startsWith("ANDROID_")) {
            String[] parts = trimmed.split("_");
            if (parts.length >= 7) {
                return trimmed;
            }
        }
        String safe = trimmed.replaceAll("[^A-Za-z0-9]", "");
        if (safe.isBlank()) safe = "app";
        return "ANDROID_Web_1.0_Device_1.0.0_" + safe + "_" + System.currentTimeMillis();
    }
}
