package com.secondlife.secondlife.service.ekyc.impl;

import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.VerificationType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.service.ekyc.EkycProviderClient;
import com.secondlife.secondlife.service.ekyc.vnpt.VnptApiException;
import com.secondlife.secondlife.service.ekyc.vnpt.VnptAuthenticationException;
import com.secondlife.secondlife.service.ekyc.vnpt.VnptEkycOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptEkycProviderClient implements EkycProviderClient {
    private static final String PROVIDER_NAME = "VNPT_EKYC";
    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    private final VnptEkycOrchestrator orchestrator;
    private final RestTemplate restTemplate;
    private final String cloudName;

    public VnptEkycProviderClient(VnptEkycOrchestrator orchestrator,
                                  @Qualifier("vnptRestTemplate") RestTemplate restTemplate,
                                  @Value("${app.cloudinary.cloud-name}") String cloudName) {
        this.orchestrator = orchestrator;
        this.restTemplate = restTemplate;
        this.cloudName = cloudName;
    }

    @Override
    public String getProviderName() { return PROVIDER_NAME; }

    @Override
    public EkycResult verify(EkycRequest request) {
        String reference = "VNPT-" + UUID.randomUUID().toString().substring(0, 8);
        if (request.documentType() == VerificationType.BUSINESS_LICENSE) {
            return EkycResult.fail(ReasonCode.UNSUPPORTED_DOCUMENT, PROVIDER_NAME, reference);
        }
        if (request.selfieUrl() == null || request.selfieUrl().isBlank()) {
            return EkycResult.uncertain(ReasonCode.SELFIE_QUALITY_LOW, PROVIDER_NAME,
                    reference, null, null, null);
        }
        if (request.clientSession() == null || request.clientSession().isBlank()
                || request.token() == null || request.token().isBlank()) {
            return EkycResult.uncertain(ReasonCode.EKYC_CONTEXT_MISSING, PROVIDER_NAME,
                    reference, null, null, null);
        }
        try {
            byte[] front = downloadImage(request.documentFrontUrl());
            byte[] back = downloadImage(request.documentBackUrl());
            byte[] selfie = downloadImage(request.selfieUrl());
            int type = request.documentType() == VerificationType.PASSPORT ? 5 : -1;
            String clientSession = normalizeClientSession(request.clientSession());
            String token = request.token() != null ? request.token().trim() : null;
            VnptResults.Verification result = orchestrator.verify(front, back, selfie,
                    clientSession, token, type);
            String extractedId = result.ocr().object().id();
            if (request.documentNumber() != null && extractedId != null
                    && !request.documentNumber().replaceAll("\\s+", "")
                    .equalsIgnoreCase(extractedId.replaceAll("\\s+", ""))) {
                return EkycResult.fail(ReasonCode.CONFIRMED_IDENTITY_MISMATCH, PROVIDER_NAME, reference);
            }
            Integer detectedType = result.ocr().object().typeId();
            if (detectedType != null && ((type == 5 && detectedType != 2)
                    || (type == -1 && detectedType != 0 && detectedType != 1
                    && detectedType != 5 && detectedType != 6))) {
                return EkycResult.fail(ReasonCode.UNSUPPORTED_DOCUMENT, PROVIDER_NAME, reference);
            }
            if (!result.ocr().object().generalWarning().isEmpty()) {
                return EkycResult.uncertain(ReasonCode.DOCUMENT_DATA_INCONSISTENCY,
                        PROVIDER_NAME, reference, null, null, null);
            }
            Double score = normalizedScore(result.faceCompare().object().prob());
            if (result.verified()) {
                return EkycResult.pass(PROVIDER_NAME, reference, score, null, null);
            }
            return switch (result.reasonCode()) {
                case "CARD_LIVENESS_FAILED" -> EkycResult.fail(ReasonCode.DOCUMENT_SUSPECTED_FAKE,
                        PROVIDER_NAME, reference);
                case "FACE_LIVENESS_FAILED" -> EkycResult.fail(ReasonCode.LIVENESS_FAILED,
                        PROVIDER_NAME, reference);
                case "FACE_MISMATCH" -> EkycResult.fail(ReasonCode.FACE_MISMATCH,
                        PROVIDER_NAME, reference);
                case "FACE_MASKED" -> EkycResult.uncertain(ReasonCode.SELFIE_QUALITY_LOW,
                        PROVIDER_NAME, reference, score, null, null);
                default -> EkycResult.uncertain(ReasonCode.OCR_LOW_CONFIDENCE,
                        PROVIDER_NAME, reference, score, null, null);
            };
        } catch (InvalidImageException | BadRequestException ex) {
            return EkycResult.uncertain(ReasonCode.IMAGE_NOT_ACCESSIBLE,
                    PROVIDER_NAME, reference, null, null, null);
        } catch (VnptAuthenticationException ex) {
            log.warn("VNPT authentication failed: endpoint={}, status={}, ref={}",
                    ex.getEndpoint(), ex.getUpstreamStatus(), reference);
            return EkycResult.providerError(ReasonCode.PROVIDER_AUTH_FAILED,
                    PROVIDER_NAME, reference, "VNPT authentication failed");
        } catch (VnptApiException ex) {
            log.warn("VNPT API failed: endpoint={}, status={}, code={}, ref={}",
                    ex.getEndpoint(), ex.getUpstreamStatus(), ex.getProviderCode(), reference);
            ReasonCode reason = (ex.getUpstreamStatus() >= 400 && ex.getUpstreamStatus() < 500)
                    ? ReasonCode.PROVIDER_REQUEST_REJECTED
                    : ex.getUpstreamStatus() == 504 ? ReasonCode.PROVIDER_TIMEOUT
                    : ReasonCode.PROVIDER_UNAVAILABLE;
            return EkycResult.providerError(reason, PROVIDER_NAME, reference,
                    "VNPT service rejected the request or is temporarily unavailable");
        } catch (ResourceAccessException ex) {
            return EkycResult.providerError(ReasonCode.PROVIDER_UNAVAILABLE,
                    PROVIDER_NAME, reference, "Could not download an eKYC image");
        }
    }

    private String normalizeClientSession(String session) {
        return VnptEkycOrchestrator.normalizeClientSession(session);
    }

    private byte[] downloadImage(String imageUrl) {
        URI uri;
        try { uri = URI.create(imageUrl); }
        catch (RuntimeException ex) { throw new InvalidImageException(); }
        String allowedPath = "/" + cloudName + "/image/upload/";
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null
                || !uri.getPath().startsWith(allowedPath)) {
            throw new InvalidImageException();
        }
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(uri, byte[].class);
            byte[] bytes = response.getBody();
            MediaType type = response.getHeaders().getContentType();
            if (!response.getStatusCode().is2xxSuccessful() || bytes == null || bytes.length == 0
                    || bytes.length > MAX_IMAGE_BYTES || type == null
                    || (!MediaType.IMAGE_JPEG.isCompatibleWith(type)
                    && !MediaType.IMAGE_PNG.isCompatibleWith(type))) {
                throw new InvalidImageException();
            }
            return bytes;
        } catch (RestClientResponseException ex) {
            throw new InvalidImageException();
        }
    }

    private Double normalizedScore(Double probability) {
        if (probability == null || !Double.isFinite(probability) || probability < 0) return null;
        return probability > 1 ? probability / 100 : probability;
    }

    private static class InvalidImageException extends RuntimeException { }
}
