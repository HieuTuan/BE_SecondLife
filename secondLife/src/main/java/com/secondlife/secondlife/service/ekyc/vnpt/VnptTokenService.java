package com.secondlife.secondlife.service.ekyc.vnpt;

import com.secondlife.secondlife.config.VnptEkycProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.LinkedHashMap;
import java.net.SocketTimeoutException;

@Service
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
public class VnptTokenService {
    private static final String TOKEN_PATH = "/auth/oauth/token";

    private final VnptEkycProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final long refreshMarginMillis;
    private String token;
    private long expiresAtMillis;

    public VnptTokenService(VnptEkycProperties properties,
                            @Qualifier("vnptRestTemplate") RestTemplate restTemplate,
                            ObjectMapper objectMapper,
                            @Value("${app.ekyc.vnpt.token-refresh-margin-millis}") long refreshMarginMillis) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.refreshMarginMillis = refreshMarginMillis;
    }

    public synchronized String getToken() {
        long now = System.currentTimeMillis();
        if (token != null && now < expiresAtMillis - refreshMarginMillis) {
            return token;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = new RedactedTokenBody();
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        body.put("grant_type", "client_credentials");
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(properties.baseUrl() + TOKEN_PATH,
                    new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String received = root.path("access_token").asText();
            long expiresIn = root.path("expires_in").asLong();
            if (received.isBlank() || expiresIn <= 0 || expiresIn > Long.MAX_VALUE / 1000) {
                throw new VnptAuthenticationException(502, TOKEN_PATH, "MALFORMED_TOKEN_RESPONSE");
            }
            token = received;
            expiresAtMillis = now + expiresIn * 1000;
            return token;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new VnptAuthenticationException(status, TOKEN_PATH, "TOKEN_REQUEST_FAILED");
            }
            throw new VnptApiException("VNPT token request failed", status, TOKEN_PATH, "TOKEN_REQUEST_FAILED");
        } catch (ResourceAccessException ex) {
            int status = isTimeout(ex) ? 504 : 503;
            throw new VnptApiException("VNPT token connection failed", status, TOKEN_PATH,
                    status == 504 ? "TIMEOUT" : "CONNECTION_FAILED");
        } catch (VnptApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new VnptAuthenticationException(502, TOKEN_PATH, "MALFORMED_TOKEN_RESPONSE");
        }
    }

    public synchronized void invalidateIfCurrent(String rejectedToken) {
        if (token != null && token.equals(rejectedToken)) {
            token = null;
            expiresAtMillis = 0;
        }
    }

    private static final class RedactedTokenBody extends LinkedHashMap<String, String> {
        @Override public String toString() { return "[redacted VNPT OAuth request]"; }
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException) return true;
        }
        return false;
    }
}
