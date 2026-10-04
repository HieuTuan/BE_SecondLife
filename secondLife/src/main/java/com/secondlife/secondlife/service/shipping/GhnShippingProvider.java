package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.config.GhnProperties;
import com.secondlife.secondlife.exception.ShippingProviderException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GhnShippingProvider implements ShippingProvider {
    private static final String STAGING_BASE = "https://dev-online-gateway.ghn.vn/shiip/public-api";
    private static final String PRODUCTION_BASE = "https://online-gateway.ghn.vn/shiip/public-api";
    private static final Set<String> APPROVED_BASES = Set.of(STAGING_BASE, PRODUCTION_BASE);
    private static final int MAX_RESPONSE_CHARACTERS = 4 * 1024 * 1024;

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;
    private final GhnProperties properties;
    private volatile RestClient restClient;

    public GhnShippingProvider(RestClient.Builder builder, ObjectMapper objectMapper, GhnProperties properties) {
        this.builder = builder;
        this.objectMapper = objectMapper;
        this.properties = properties;

        Duration timeout = Duration.ofMillis(properties.getTimeoutMs());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        builder.requestFactory(requestFactory);
    }

    @Override
    public JsonNode provinces() {
        return get("/v3/master-data/province/all?offset=0&limit=200");
    }

    @Override
    public JsonNode legacyProvinces() {
        return get("/master-data/province");
    }

    @Override
    public JsonNode wards(int provinceId) {
        positiveId(provinceId);
        return get("/v3/master-data/ward/all-by-province-id?province_id=" + provinceId + "&offset=0&limit=200");
    }

    @Override
    public JsonNode districts(int provinceId) {
        positiveId(provinceId);
        return get("/master-data/district?province_id=" + provinceId);
    }

    @Override
    public JsonNode legacyWards(int districtId) {
        positiveId(districtId);
        return get("/master-data/ward?district_id=" + districtId);
    }

    @Override
    public JsonNode services(int fromDistrictId, int toDistrictId) {
        positiveId(fromDistrictId);
        positiveId(toDistrictId);
        return post("/v2/shipping-order/available-services", Map.of(
                "shop_id", properties.getShopId(), "from_district", fromDistrictId, "to_district", toDistrictId));
    }

    @Override
    public JsonNode quote(Map<String, Object> payload) {
        return post("/v2/shipping-order/fee", payload);
    }

    @Override
    public JsonNode preview(Map<String, Object> payload) {
        return post("/v2/shipping-order/preview", payload);
    }

    @Override
    public JsonNode leadtime(Map<String, Object> payload) {
        return post("/v2/shipping-order/leadtime", payload);
    }

    @Override
    public JsonNode create(Map<String, Object> payload) {
        // The caller persists client_order_code before calling GHN and reuses it when reconciling an uncertain result.
        return post("/v2/shipping-order/create", payload);
    }

    @Override
    public JsonNode detail(String orderCode) {
        validOrderCode(orderCode);
        return get("/v2/shipping-order/detail?order_code=" + orderCode);
    }

    @Override
    public JsonNode cancel(String orderCode) {
        return changeStatus("/v2/switch-status/cancel", orderCode);
    }

    @Override
    public JsonNode returnToSender(String orderCode) {
        return changeStatus("/v2/switch-status/return", orderCode);
    }

    @Override
    public JsonNode label(String orderCode) {
        validOrderCode(orderCode);
        JsonNode data = post("/v2/a5/gen-token", Map.of("order_codes", List.of(orderCode)));
        JsonNode token = data.path("token");
        if (!token.isTextual() || token.asText().isBlank() || token.asText().length() > 4096) {
            throw malformedResponse();
        }
        String origin = STAGING_BASE.equals(configuration().baseUrl())
                ? "https://dev-online-gateway.ghn.vn" : "https://online-gateway.ghn.vn";
        String url = UriComponentsBuilder.fromUriString(origin)
                .path("/a5/public-api/printA5")
                .queryParam("token", "{token}")
                .encode().buildAndExpand(token.asText()).toUriString();
        return objectMapper.createObjectNode().put("url", url);
    }

    private JsonNode changeStatus(String path, String orderCode) {
        validOrderCode(orderCode);
        JsonNode data = post(path, Map.of("order_codes", List.of(orderCode)));
        if (!data.isArray() || data.size() != 1
                || !orderCode.equals(data.get(0).path("order_code").asText())
                || !data.get(0).path("result").isBoolean() || !data.get(0).path("result").asBoolean()) {
            throw new ShippingProviderException("GHN rejected the shipment operation");
        }
        return data;
    }

    private JsonNode get(String path) {
        return request(HttpMethod.GET, path, null);
    }

    private JsonNode post(String path, Map<String, Object> payload) {
        if (payload == null) throw new ShippingProviderException("Invalid GHN request");
        return request(HttpMethod.POST, path, payload);
    }

    private JsonNode request(HttpMethod method, String path, Map<String, Object> payload) {
        Gateway configuration = configuration();
        try {
            RestClient.RequestBodySpec request = client().method(method)
                    .uri(URI.create(configuration.baseUrl() + path))
                    .header("Token", configuration.token())
                    .header("ShopId", Integer.toString(configuration.shopId()))
                    .accept(MediaType.APPLICATION_JSON);
            if (payload != null) {
                byte[] serializedPayload = objectMapper.writeValueAsBytes(payload);
                // Streaming avoids RestClient's DEBUG logging of recipient data through the message converters.
                request.contentType(MediaType.APPLICATION_JSON).body(outputStream -> outputStream.write(serializedPayload));
            }
            String body = request.retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (httpRequest, response) -> {
                        throw new ShippingProviderException("GHN request failed", response.getStatusCode().value());
                    })
                    .body(String.class);
            return unwrap(body);
        } catch (ShippingProviderException error) {
            throw error;
        } catch (ResourceAccessException error) {
            throw new ShippingProviderException(isTimeout(error) ? "GHN request timed out" : "GHN is unavailable",
                    isTimeout(error) ? 504 : 502);
        } catch (RestClientException | JacksonException | IllegalArgumentException error) {
            // Do not retain the upstream cause: it can contain credentials, request data, or response bodies.
            throw new ShippingProviderException("GHN returned an invalid response");
        }
    }

    private JsonNode unwrap(String body) {
        if (body == null || body.isBlank() || body.length() > properties.getMaxResponseCharacters()) throw malformedResponse();
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.isObject()
                || !root.path("code").isIntegralNumber() || !root.path("code").canConvertToInt()
                || root.path("code").asInt() != 200) {
            throw malformedResponse();
        }
        JsonNode data = root.path("data");
        if (!data.isObject() && !data.isArray()) throw malformedResponse();
        return data;
    }

    private Gateway configuration() {
        if (!properties.isEnabled()) throw new ShippingProviderException("GHN shipping is disabled", 503);
        String token = properties.getToken();
        if (token == null || token.isBlank() || token.contains("\r") || token.contains("\n") || properties.getShopId() <= 0) {
            throw new ShippingProviderException("GHN shipping is not configured", 503);
        }
        String baseUrl = properties.getBaseUrl();
        if (baseUrl != null && baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        if (baseUrl == null || !APPROVED_BASES.contains(baseUrl)) {
            throw new ShippingProviderException("GHN gateway configuration is invalid", 503);
        }
        return new Gateway(baseUrl, token, properties.getShopId());
    }

    private RestClient client() {
        RestClient current = restClient;
        if (current == null) {
            synchronized (this) {
                current = restClient;
                if (current == null) restClient = current = builder.build();
            }
        }
        return current;
    }

    private void positiveId(int value) {
        if (value <= 0) throw new ShippingProviderException("Invalid GHN address identifier");
    }

    private void validOrderCode(String orderCode) {
        if (orderCode == null || !orderCode.matches("[A-Za-z0-9_-]{1,50}")) {
            throw new ShippingProviderException("Invalid GHN order code");
        }
    }

    private ShippingProviderException malformedResponse() {
        return new ShippingProviderException("GHN returned an invalid response");
    }

    private boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) return true;
        }
        return false;
    }

    private record Gateway(String baseUrl, String token, int shopId) { }
}
