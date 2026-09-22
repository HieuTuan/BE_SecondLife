package com.secondlife.secondlife.service.ekyc.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.secondlife.secondlife.dto.ekyc.EkycRequest;
import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.service.ekyc.EkycProviderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptEkycProviderClient implements EkycProviderClient {

    private static final String PROVIDER_NAME = "VNPT_EKYC";

    private final String baseUrl;
    private final String tokenUrl;
    private final String appClientId;
    private final String appClientSecret;
    private final String serverClientId;
    private final String serverClientSecret;
    private final int timeoutMs;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private String cachedToken;
    private long tokenExpiresAtMillis = 0;

    public VnptEkycProviderClient(
            @Value("${app.ekyc.base-url}") String baseUrl,
            @Value("${app.ekyc.token-url}") String tokenUrl,
            @Value("${app.ekyc.app-client-id}") String appClientId,
            @Value("${app.ekyc.app-client-secret}") String appClientSecret,
            @Value("${app.ekyc.server-client-id}") String serverClientId,
            @Value("${app.ekyc.server-client-secret}") String serverClientSecret,
            @Value("${app.ekyc.timeout-ms}") int timeoutMs,
            @org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "";
        this.tokenUrl = tokenUrl;
        this.appClientId = appClientId;
        this.appClientSecret = appClientSecret;
        this.serverClientId = serverClientId;
        this.serverClientSecret = serverClientSecret;
        this.timeoutMs = timeoutMs;
        this.objectMapper = (objectMapper != null) ? objectMapper : tools.jackson.databind.json.JsonMapper.builder().build();

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public EkycResult verify(EkycRequest request) {
        String refId = "VNPT-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Initiating VNPT eKYC verification for documentType: {}, ref: {}",
                request.documentType(), refId);

        try {
            // 1. Obtain / Refresh VNPT OAuth2 Access Token (if tokenUrl is configured)
            String accessToken = null;
            if (tokenUrl != null && !tokenUrl.isBlank()) {
                try {
                    accessToken = getAccessToken();
                } catch (Exception e) {
                    log.warn("Failed to get OAuth2 token from VNPT ({}), falling back to header-based auth: {}",
                            tokenUrl, e.getMessage());
                }
            }

            // 2. Download Document Images
            byte[] frontBytes = downloadImageBytes(request.documentFrontUrl());
            byte[] backBytes = downloadImageBytes(request.documentBackUrl());

            // 3. Call VNPT OCR API
            JsonNode ocrResponse = callVnptOcrApi(accessToken, frontBytes, backBytes);
            log.info("VNPT OCR response received for ref: {}", refId);

            // 4. Analyze OCR Results & Card Quality
            EkycResult qualityOrFraudResult = evaluateOcrQualityAndFraud(ocrResponse, request.documentNumber(), refId);
            if (qualityOrFraudResult != null) {
                return qualityOrFraudResult;
            }

            // 5. Call VNPT Face Compare API if selfie is present
            Double faceMatchScore = null;
            if (request.selfieUrl() != null && !request.selfieUrl().isBlank()) {
                byte[] selfieBytes = downloadImageBytes(request.selfieUrl());
                JsonNode faceResponse = callVnptFaceCompareApi(accessToken, frontBytes, selfieBytes);
                faceMatchScore = extractFaceMatchScore(faceResponse);

                log.info("VNPT Face compare similarity score: {} for ref: {}", faceMatchScore, refId);

                if (faceMatchScore != null) {
                    if (faceMatchScore < 0.70) {
                        log.warn("VNPT Face compare failed (score: {}) for ref: {}", faceMatchScore, refId);
                        return EkycResult.fail(ReasonCode.FACE_MISMATCH, PROVIDER_NAME, refId);
                    } else if (faceMatchScore < 0.85) {
                        log.info("VNPT Face compare borderline (score: {}) for ref: {}", faceMatchScore, refId);
                        return EkycResult.uncertain(ReasonCode.FACE_MATCH_BORDERLINE, PROVIDER_NAME, refId, faceMatchScore, 0.90, 0.90);
                    }
                }
            }

            // All checks passed
            double finalFaceScore = faceMatchScore != null ? faceMatchScore : 0.95;
            return EkycResult.pass(PROVIDER_NAME, refId, finalFaceScore, 0.98, 0.95);

        } catch (ResourceAccessException ex) {
            log.error("Network or timeout error calling VNPT eKYC: {}", ex.getMessage());
            if (ex.getCause() instanceof SocketTimeoutException) {
                return EkycResult.providerError(ReasonCode.PROVIDER_TIMEOUT, PROVIDER_NAME, refId,
                        "Hệ thống VNPT eKYC quá thời gian phản hồi (timeout)");
            }
            return EkycResult.providerError(ReasonCode.PROVIDER_UNAVAILABLE, PROVIDER_NAME, refId,
                    "Cổng kết nối VNPT eKYC tạm thời gián đoạn");
        } catch (RestClientResponseException ex) {
            log.error("VNPT eKYC HTTP error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is5xxServerError()) {
                return EkycResult.providerError(ReasonCode.PROVIDER_UNAVAILABLE, PROVIDER_NAME, refId,
                        "Hệ thống VNPT eKYC đang bảo trì hoặc gặp sự cố máy chủ");
            }
            return EkycResult.providerError(ReasonCode.PROVIDER_UNAVAILABLE, PROVIDER_NAME, refId,
                    "Lỗi xác thực hoặc yêu cầu không hợp lệ từ cổng VNPT eKYC: " + ex.getStatusText());
        } catch (Exception ex) {
            log.error("Unexpected error during VNPT eKYC verification: {}", ex.getMessage(), ex);
            return EkycResult.providerError(ReasonCode.PROVIDER_UNAVAILABLE, PROVIDER_NAME, refId,
                    "Lỗi xử lý xác thực danh tính: " + ex.getMessage());
        }
    }

    /**
     * Obtains or reuses an existing cached VNPT OAuth2 access token.
     */
    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpiresAtMillis - 60000) {
            return cachedToken;
        }

        log.info("Fetching new VNPT access token from: {}", tokenUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        String effectiveClientId = (appClientId != null && !appClientId.isBlank()) ? appClientId : serverClientId;
        String effectiveSecret = (appClientSecret != null && !appClientSecret.isBlank()) ? appClientSecret : serverClientSecret;

        if (effectiveClientId != null && effectiveSecret != null) {
            String credentials = effectiveClientId + ":" + effectiveSecret;
            String encodedAuth = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedAuth);
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        if (effectiveClientId != null) body.add("client_id", effectiveClientId);
        if (effectiveSecret != null) body.add("client_secret", effectiveSecret);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, requestEntity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            String token = null;
            if (root.has("access_token")) {
                token = root.get("access_token").asText();
            } else if (root.has("id_token")) {
                token = root.get("id_token").asText();
            } else if (root.has("token")) {
                token = root.get("token").asText();
            }

            if (token == null || token.isBlank()) {
                throw new IllegalStateException("No access token found in VNPT token response");
            }

            long expiresInSeconds = root.has("expires_in") ? root.get("expires_in").asLong() : 3600;
            this.cachedToken = token;
            this.tokenExpiresAtMillis = now + (expiresInSeconds * 1000);

            log.info("Successfully acquired VNPT access token, valid for {} seconds", expiresInSeconds);
            return token;

        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse VNPT token response: " + ex.getMessage(), ex);
        }
    }

    /**
     * Calls VNPT ID OCR endpoint.
     */
    private JsonNode callVnptOcrApi(String token, byte[] frontBytes, byte[] backBytes) throws Exception {
        String url = baseUrl + "/ai/v1/ocr/id";

        HttpHeaders headers = buildVnptHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("img_front", new NamedByteArrayResource(frontBytes, "front.jpg"));
        body.add("img_back", new NamedByteArrayResource(backBytes, "back.jpg"));
        body.add("client_session", UUID.randomUUID().toString());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        return objectMapper.readTree(response.getBody());
    }

    /**
     * Calls VNPT Face Compare endpoint.
     */
    private JsonNode callVnptFaceCompareApi(String token, byte[] frontBytes, byte[] selfieBytes) throws Exception {
        String url = baseUrl + "/ai/v1/face/compare";

        HttpHeaders headers = buildVnptHeaders(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("img_front", new NamedByteArrayResource(frontBytes, "front.jpg"));
        body.add("img_face", new NamedByteArrayResource(selfieBytes, "selfie.jpg"));
        body.add("client_session", UUID.randomUUID().toString());

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        return objectMapper.readTree(response.getBody());
    }

    private HttpHeaders buildVnptHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        if (serverClientId != null && !serverClientId.isBlank()) {
            headers.set("token-id", serverClientId);
        }
        if (serverClientSecret != null && !serverClientSecret.isBlank()) {
            headers.set("token-key", serverClientSecret);
        }
        if (appClientId != null && !appClientId.isBlank()) {
            headers.set("app-id", appClientId);
        }
        if (appClientSecret != null && !appClientSecret.isBlank()) {
            headers.set("app-key", appClientSecret);
        }
        headers.set("mac-address", "default");
        return headers;
    }

    private EkycResult evaluateOcrQualityAndFraud(JsonNode ocrRoot, String expectedDocNumber, String refId) {
        if (ocrRoot == null) {
            return EkycResult.uncertain(ReasonCode.AMBIGUOUS_IDENTITY_RESULT, PROVIDER_NAME, refId, null, null, null);
        }

        JsonNode objectNode = ocrRoot.has("object") ? ocrRoot.get("object") : ocrRoot;

        // Check if document is expired
        if (isWarningTriggered(objectNode, "expire_warning") || isWarningTriggered(objectNode, "expired")) {
            log.warn("VNPT reported document expired for ref: {}", refId);
            return EkycResult.fail(ReasonCode.DOCUMENT_EXPIRED, PROVIDER_NAME, refId);
        }

        // Check tampering / fake document
        if (isWarningTriggered(objectNode, "tampering") || isWarningTriggered(objectNode, "id_fake")
                || isWarningTriggered(objectNode, "fake")) {
            log.warn("VNPT reported suspected fake document for ref: {}", refId);
            return EkycResult.fail(ReasonCode.DOCUMENT_SUSPECTED_FAKE, PROVIDER_NAME, refId);
        }

        // Check user-fixable quality issues
        if (isWarningTriggered(objectNode, "blur") || isWarningTriggered(objectNode, "blurry")) {
            return EkycResult.uncertain(ReasonCode.IMAGE_TOO_BLURRY, PROVIDER_NAME, refId, null, null, 0.40);
        }
        if (isWarningTriggered(objectNode, "glare") || isWarningTriggered(objectNode, "reflection")) {
            return EkycResult.uncertain(ReasonCode.IMAGE_GLARE, PROVIDER_NAME, refId, null, null, 0.45);
        }
        if (isWarningTriggered(objectNode, "corner_cut") || isWarningTriggered(objectNode, "corner")) {
            return EkycResult.uncertain(ReasonCode.DOCUMENT_NOT_FULLY_VISIBLE, PROVIDER_NAME, refId, null, null, 0.50);
        }

        // Check extracted document number vs input document number
        if (expectedDocNumber != null && !expectedDocNumber.isBlank()) {
            String extractedId = objectNode.has("id") ? objectNode.get("id").asText().trim() : null;
            if (extractedId != null && !extractedId.isBlank()) {
                String cleanExtracted = extractedId.replaceAll("\\s+", "");
                String cleanExpected = expectedDocNumber.trim().replaceAll("\\s+", "");
                if (!cleanExtracted.equalsIgnoreCase(cleanExpected)) {
                    log.warn("VNPT extracted ID {} does not match user input ID {} for ref: {}",
                            cleanExtracted, cleanExpected, refId);
                    return EkycResult.fail(ReasonCode.CONFIRMED_IDENTITY_MISMATCH, PROVIDER_NAME, refId);
                }
            }
        }

        return null; // Passes all OCR quality & fraud checks
    }

    private Double extractFaceMatchScore(JsonNode faceRoot) {
        if (faceRoot == null) return null;
        JsonNode node = faceRoot.has("object") ? faceRoot.get("object") : faceRoot;
        if (node.has("similarity")) {
            double raw = node.get("similarity").asDouble();
            return (raw > 1.0) ? raw / 100.0 : raw;
        }
        if (node.has("score")) {
            double raw = node.get("score").asDouble();
            return (raw > 1.0) ? raw / 100.0 : raw;
        }
        return null;
    }

    private boolean isWarningTriggered(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) return false;
        JsonNode field = node.get(fieldName);
        if (field.isBoolean()) return field.asBoolean();
        if (field.isNumber()) return field.asInt() > 0;
        String text = field.asText().trim().toLowerCase();
        return text.equals("yes") || text.equals("true") || text.equals("1") || text.contains("warning");
    }

    private byte[] downloadImageBytes(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return new byte[0];
        }
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(imageUrl, byte[].class);
            return response.getBody() != null ? response.getBody() : new byte[0];
        } catch (Exception ex) {
            log.warn("Could not download image from URL {}: {}", imageUrl, ex.getMessage());
            return new byte[0];
        }
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        public NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
