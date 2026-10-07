package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.config.VnptEkycProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.LinkedHashMap;
import java.net.SocketTimeoutException;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptHttpClient {
    private static final String SUCCESS_CODE = "IDG-00000000";

    private final VnptEkycProperties properties;
    private final VnptTokenService tokenService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public VnptHttpClient(VnptEkycProperties properties, VnptTokenService tokenService,
                          @Qualifier("vnptRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public JsonNode postJson(String path, Map<String, ?> body) {
        return post(path, new RedactedJsonBody(body), MediaType.APPLICATION_JSON);
    }

    public JsonNode postMultipart(String path, MultiValueMap<String, Object> body) {
        MultiValueMap<String, Object> redacted = new RedactedMultipartBody();
        body.forEach((key, values) -> values.forEach(value -> redacted.add(key, value)));
        return post(path, redacted, MediaType.MULTIPART_FORM_DATA);
    }

    private JsonNode post(String path, Object body, MediaType contentType) {
        // Authentication permits one retry with a renewed token after HTTP 401.
        for (int attempt = 0; attempt < 2; attempt++) {
            String accessToken = tokenService.getToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.set("Token-id", properties.tokenId());
            headers.set("Token-key", properties.tokenKey());
            headers.set("mac-address", properties.macAddress());
            headers.setContentType(contentType);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            long started = System.nanoTime();
            try {
                ResponseEntity<String> response = restTemplate.postForEntity(properties.baseUrl() + path,
                        new HttpEntity<>(body, headers), String.class);
                JsonNode root = objectMapper.readTree(response.getBody());
                if (root == null || !SUCCESS_CODE.equals(root.path("message").asText())
                        || (root.has("statusCode") && root.path("statusCode").asInt() != 200)) {
                    int status = 400;
                    if (root != null && root.has("statusCode") && root.path("statusCode").asInt() > 0) {
                        status = root.path("statusCode").asInt();
                    } else if (root == null) {
                        status = 502;
                    }
                    String providerCode = root != null ? findProviderCode(root, 0) : null;
                    if (providerCode == null && root != null) {
                        providerCode = safeCode(root.path("message").asText(null));
                    }
                    if (providerCode == null || "UNAVAILABLE".equals(providerCode)) {
                        String logId = root != null ? root.path("logID").asText("") : "";
                        providerCode = logId.contains("Zuulserver") ? "ZUUL_GATEWAY_REJECTED" : "REJECTED_BY_PROVIDER";
                    }
                    String errorDetail = root != null ? providerErrorSummary(root) : "empty body";
                    log.warn("VNPT request rejected: endpoint={}, status={}, durationMs={}, code={}, detail={}",
                            path, status, (System.nanoTime() - started) / 1_000_000,
                            providerCode, errorDetail);
                    throw new VnptApiException("VNPT returned an unsuccessful response", status, path, providerCode);
                }
                log.info("VNPT request completed: endpoint={}, status=200, durationMs={}",
                        path, (System.nanoTime() - started) / 1_000_000);
                return root;
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                String providerCode = providerCode(ex);
                log.warn("VNPT request failed: endpoint={}, status={}, durationMs={}, code={}, detail={}",
                        path, status, (System.nanoTime() - started) / 1_000_000,
                        providerCode, providerErrorSummary(ex));
                if (status == 401) {
                    tokenService.invalidateIfCurrent(accessToken);
                    if (attempt == 0) continue;
                    throw new VnptAuthenticationException(status, path, "UNAUTHORIZED");
                }
                if (status == 403) throw new VnptAuthenticationException(status, path, "FORBIDDEN");
                throw new VnptApiException("VNPT request failed", status, path, providerCode);
            } catch (ResourceAccessException ex) {
                log.warn("VNPT connection failed: endpoint={}, durationMs={}",
                        path, (System.nanoTime() - started) / 1_000_000);
                int status = isTimeout(ex) ? 504 : 503;
                throw new VnptApiException("VNPT connection failed", status, path,
                        status == 504 ? "TIMEOUT" : "CONNECTION_FAILED");
            } catch (VnptApiException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new VnptApiException("Malformed VNPT response", 502, path, "MALFORMED_RESPONSE");
            }
        }
        throw new VnptAuthenticationException(401, path, "UNAUTHORIZED");
    }

    private String providerCode(RestClientResponseException ex) {
        try {
            JsonNode root = objectMapper.readTree(ex.getResponseBodyAsString());
            String code = findProviderCode(root, 0);
            return code != null ? code : "UNAVAILABLE";
        } catch (RuntimeException ignored) {
            return "UNAVAILABLE";
        }
    }

    private String providerErrorSummary(RestClientResponseException ex) {
        String responseBody = ex.getResponseBodyAsString();
        String contentType = ex.getResponseHeaders() != null
                ? String.valueOf(ex.getResponseHeaders().getContentType()) : "unknown";
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String summary = providerErrorSummary(root);
            if (!summary.startsWith("root=")) return summary;
            return "no recognized error fields; contentType=" + contentType
                    + "; bodyLength=" + responseBody.length() + "; " + summary;
        } catch (RuntimeException ignored) {
            String plainText = sanitizeDetail(responseBody.replaceAll("<[^>]*>", " "));
            String excerpt = plainText.isEmpty() ? "empty" : plainText.substring(0, Math.min(180, plainText.length()));
            return "non-JSON response; contentType=" + contentType
                    + "; bodyLength=" + responseBody.length() + "; excerpt=" + excerpt;
        }
    }

    private String providerErrorSummary(JsonNode root) {
        if (root == null) return "empty response";
        StringBuilder summary = new StringBuilder();
        collectErrorDetails(root, summary, 0);
        String logId = root.path("logID").asText(null);
        if (logId != null && logId.matches("[A-Za-z0-9._:-]{1,128}")) {
            if (!summary.isEmpty()) summary.append("; ");
            summary.append("logID=").append(logId);
        }
        if (!summary.isEmpty()) return summary.toString();
        return "root=" + rootType(root) + "; rootKeys=" + rootKeys(root);
    }

    private void collectErrorDetails(JsonNode node, StringBuilder summary, int depth) {
        if (node == null || depth > 6 || summary.length() >= 700) return;
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if (summary.length() >= 700) return;
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isDiagnosticField(key) && isScalar(value)) {
                    appendDetail(summary, key, value.asText());
                } else if (isDiagnosticField(key) && value.isArray()) {
                    value.forEach(item -> {
                        if (isScalar(item)) appendDetail(summary, key, item.asText());
                        else collectErrorDetails(item, summary, depth + 1);
                    });
                } else {
                    collectErrorDetails(value, summary, depth + 1);
                }
            });
        } else if (node.isArray()) {
            node.forEach(item -> collectErrorDetails(item, summary, depth + 1));
        }
    }

    private String findProviderCode(JsonNode node, int depth) {
        if (node == null || depth > 6) return null;
        if (node.isObject()) {
            for (var entry : node.properties()) {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isCodeField(key) && isScalar(value)) {
                    String code = safeCode(value.asText());
                    if (!"UNAVAILABLE".equals(code)) return code;
                }
                String nested = findProviderCode(value, depth + 1);
                if (nested != null) return nested;
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = findProviderCode(item, depth + 1);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private boolean isDiagnosticField(String key) {
        return switch (key.toLowerCase()) {
            case "message", "msg", "error", "error_description", "errors", "detail", "description", "reason" -> true;
            default -> false;
        };
    }

    private boolean isCodeField(String key) {
        return switch (key.toLowerCase()) {
            case "code", "errorcode", "error_code" -> true;
            default -> false;
        };
    }

    private boolean isScalar(JsonNode value) {
        return value != null && (value.isTextual() || value.isNumber() || value.isBoolean());
    }

    private String rootType(JsonNode root) {
        if (root.isObject()) return "object";
        if (root.isArray()) return "array";
        if (root.isTextual()) return "text";
        return "scalar";
    }

    private String rootKeys(JsonNode root) {
        if (!root.isObject()) return "n/a";
        StringBuilder keys = new StringBuilder();
        int count = 0;
        for (var entry : root.properties()) {
            if (count++ >= 12) break;
            if (!keys.isEmpty()) keys.append(',');
            keys.append(entry.getKey().replaceAll("[^A-Za-z0-9_.-]", ""));
        }
        return keys.isEmpty() ? "none" : keys.toString();
    }

    private void appendDetail(StringBuilder summary, String field, String value) {
        String sanitized = sanitizeDetail(value);
        if (sanitized.isEmpty()) return;
        if (!summary.isEmpty()) summary.append("; ");
        summary.append(field).append('=').append(sanitized, 0, Math.min(sanitized.length(), 180));
    }

    private String sanitizeDetail(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)(access[_-]?token|token[_-]?key|token|authorization|client[_-]?(?:secret|session|id)|password)\\s*[\\\"']?\\s*[:=]\\s*[\\\"']?[^,;\\\"'}\\s]+", "$1=[redacted]")
                .replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "[email]")
                .replaceAll("\\d{5,}", "[number]")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String safeCode(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,64}") ? value : "UNAVAILABLE";
    }

    private static final class RedactedJsonBody extends LinkedHashMap<String, Object> {
        private RedactedJsonBody(Map<String, ?> body) { super(body); }
        @Override public String toString() { return "[redacted VNPT API request]"; }
    }

    private static final class RedactedMultipartBody extends LinkedMultiValueMap<String, Object> {
        @Override public String toString() { return "[redacted VNPT multipart request]"; }
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException) return true;
        }
        return false;
    }
}
