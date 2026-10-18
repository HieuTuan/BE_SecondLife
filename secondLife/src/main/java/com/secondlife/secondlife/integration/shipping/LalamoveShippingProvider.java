package com.secondlife.secondlife.integration.shipping;

import com.secondlife.secondlife.config.LalamoveProperties;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.exception.LalamoveRequestException;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class LalamoveShippingProvider implements ShippingProvider {
    private static final BigDecimal MAX_FEE = BigDecimal.valueOf(1_000_000_000L);
    private static final Set<String> STATUSES = Set.of("ASSIGNING_DRIVER", "ON_GOING", "PICKED_UP", "COMPLETED", "CANCELED", "REJECTED", "EXPIRED");
    private final RestClient.Builder builder;
    private final ObjectMapper mapper;
    private final LalamoveProperties properties;
    private volatile RestClient restClient;

    public LalamoveShippingProvider(RestClient.Builder builder, ObjectMapper mapper, LalamoveProperties properties) {
        this.builder = builder;
        this.mapper = mapper;
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        Duration timeout = Duration.ofMillis(properties.getTimeoutMs());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        builder.requestFactory(factory);
    }

    @Override public String code() { return "LALAMOVE"; }

    @Override public JsonNode quote(Map<String, Object> payload) { return preview(payload); }

    @Override public JsonNode preview(Map<String, Object> payload) {
        configuration();
        if (payload == null) throw new BadRequestException("Shipping route is required");
        phone(payload.get("from_phone"), "Seller pickup phone number");
        phone(payload.get("to_phone"), "Buyer delivery phone number");
        String serviceType = string(payload.get("service_type"));
        if (serviceType.isBlank() && payload.get("provider_quote")!=null)
            serviceType=mapper.valueToTree(payload.get("provider_quote")).path("serviceType").asText();
        // Validate stops before catalogue requests; contacts/coordinates never reach unrelated calls.
        var stops=List.of(stop(payload,"from"),stop(payload,"to"));
        if (serviceType.isBlank()) return automaticPreview(payload,stops);
        return previewForService(serviceType,stops);
    }

    private JsonNode automaticPreview(Map<String,Object> payload,List<Map<String,Object>> stops) {
        LalamoveVehicleSelector.validateParcel(payload);
        var candidates=LalamoveVehicleSelector.eligible(cities(),payload);
        JsonNode best=null;
        for(String service:candidates) {
            JsonNode result;
            try { result=previewForService(service,stops); }
            catch(LalamoveRequestException error) {
                if(error.getUpstreamStatus()==422 && Set.of("ERR_OUT_OF_SERVICE_AREA","ERR_INVALID_SERVICE_TYPE").contains(error.getErrorCode()==null?"":error.getErrorCode())) continue;
                throw error;
            }
            if(best==null || result.path("total_fee").decimalValue().compareTo(best.path("total_fee").decimalValue())<0) best=result;
        }
        if(best==null) throw new BadRequestException("Lalamove chưa hỗ trợ tuyến giao hàng này với các loại xe phù hợp");
        if(!Instant.parse(best.path("expires_at").asText()).isAfter(Instant.now()))
            throw new ConflictException("Báo giá vận chuyển đã hết hạn; vui lòng lấy lại báo giá trước khi thanh toán");
        return best;
    }

    private JsonNode previewForService(String serviceType,List<Map<String,Object>> stops) {
        if (serviceType == null || !serviceType.matches("[A-Z][A-Z0-9_]{0,59}"))
            throw new BadRequestException("Select a valid Lalamove service type");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("serviceType", serviceType);
        data.put("language", properties.getLanguage());
        data.put("stops", stops);
        JsonNode nativeQuote = request(HttpMethod.POST, "/v3/quotations", Map.of("data", data));
        validateQuotation(nativeQuote);
        if(!serviceType.equals(nativeQuote.path("serviceType").asText())) throw malformed();
        ObjectNode result = mapper.createObjectNode();
        result.put("total_fee", fee(nativeQuote));
        result.put("expires_at", quotationExpiry(nativeQuote).toString());
        result.set("provider_quote", nativeQuote);
        return result;
    }

    @Override public JsonNode create(Map<String, Object> payload) {
        configuration();
        if (payload == null || payload.get("provider_quote") == null)
            throw new ConflictException("Obtain a current Lalamove quotation before creating the shipment");
        JsonNode quotation = payload.get("provider_quote") instanceof JsonNode node ? node : mapper.valueToTree(payload.get("provider_quote"));
        validateQuotation(quotation);
        if (!quotationExpiry(quotation).isAfter(Instant.now()))
            throw new ConflictException("Lalamove quotation expired; refresh the shipping quotation before creating the shipment");
        String clientCode = requiredString(payload.get("client_order_code"), "Shipment request reference", 100);
        JsonNode stops = quotation.path("stops");
        Map<String, Object> sender = Map.of("stopId", identifier(stops.get(0).path("stopId")),
                "name", requiredString(payload.get("from_name"), "Sender name", 100), "phone", phone(payload.get("from_phone"), "Seller pickup phone number"));
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("stopId", identifier(stops.get(stops.size() - 1).path("stopId")));
        recipient.put("name", requiredString(payload.get("to_name"), "Recipient name", 100));
        recipient.put("phone", phone(payload.get("to_phone"), "Buyer delivery phone number"));
        String remarks = string(payload.get("content"));
        if (!remarks.isBlank()) recipient.put("remarks", remarks.substring(0, Math.min(1500, remarks.length())));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("quotationId", identifier(quotation.path("quotationId")));
        data.put("sender", sender);
        data.put("recipients", List.of(recipient));
        data.put("isPODEnabled", true);
        // This is a correlation reference, not a provider idempotency guarantee.
        data.put("metadata", Map.of("secondlifeClientOrderCode", clientCode));
        return normalizeOrder(request(HttpMethod.POST, "/v3/orders", Map.of("data", data)));
    }

    @Override public JsonNode detail(String orderCode) {
        String id = identifier(orderCode);
        JsonNode result = normalizeOrder(request(HttpMethod.GET, "/v3/orders/" + id, null));
        if (!id.equals(result.path("order_code").asText())) throw malformed();
        return result;
    }

    @Override public JsonNode cancel(String orderCode) {
        String id = identifier(orderCode);
        JsonNode data = request(HttpMethod.DELETE, "/v3/orders/" + id, null);
        if (!data.isEmpty()) {
            JsonNode result = normalizeOrder(data);
            if (!id.equals(result.path("order_code").asText()) || !"CANCELED".equals(result.path("status").asText())) throw malformed();
            return result;
        }
        return mapper.createObjectNode().put("order_code", id).put("status", "CANCELED");
    }

    @Override public JsonNode cities() { return request(HttpMethod.GET, "/v3/cities", null); }

    @Override public JsonNode driver(String orderCode, String driverId) {
        return request(HttpMethod.GET, "/v3/orders/" + identifier(orderCode) + "/drivers/" + identifier(driverId), null);
    }

    @Override public JsonNode label(String orderCode) { throw new ConflictException("Lalamove does not provide printable shipment labels; use the shipment share link"); }
    @Override public JsonNode returnToSender(String orderCode) { throw new ConflictException("Lalamove return-to-sender requires staff review and a separate return shipment"); }

    private JsonNode request(HttpMethod method, String path, Map<String, Object> payload) {
        String origin = configuration();
        try {
            String body = payload == null ? "" : mapper.writeValueAsString(payload);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String timestamp = Long.toString(Instant.now().toEpochMilli());
            String signature = LalamoveSignatures.sign(properties.getSecret(), timestamp, method.name(), path, body);
            RestClient.RequestBodySpec request = client().method(method).uri(URI.create(origin + path))
                    .header("Authorization", "hmac " + properties.getKey() + ":" + timestamp + ":" + signature)
                    .header("Market", properties.getMarket()).header("Request-ID", UUID.randomUUID().toString())
                    .accept(MediaType.APPLICATION_JSON);
            if (payload != null) {
                // Sign and stream the same bytes; message-converter DEBUG logs must not receive contacts.
                request.contentType(MediaType.APPLICATION_JSON).body(output -> output.write(bytes));
            }
            return request.exchange((httpRequest, response) -> {
                if (!response.getStatusCode().is2xxSuccessful())
                    throw rejection(response.getStatusCode().value(),response.getBody());
                if (method == HttpMethod.DELETE && response.getStatusCode().value() == 204)
                    return mapper.createObjectNode();
                String responseBody = readBounded(response.getBody());
                JsonNode root = mapper.readTree(responseBody);
                if (root == null || !root.isObject() || (!root.path("data").isObject() && !root.path("data").isArray())) throw malformed();
                if (method == HttpMethod.DELETE && root.path("data").isEmpty()) throw malformed();
                return root.path("data");
            });
        } catch (ShippingProviderException error) {
            throw error;
        } catch (ResourceAccessException error) {
            boolean timeout = isTimeout(error);
            throw new ShippingProviderException(timeout ? "Lalamove request timed out" : "Lalamove is unavailable", timeout ? 504 : 502);
        } catch (RestClientException | JacksonException | IllegalArgumentException error) {
            // Never retain upstream causes: they can contain Authorization headers and delivery contacts.
            throw malformed();
        }
    }

    private LalamoveRequestException rejection(int status,InputStream stream) {
        try {
            JsonNode root=mapper.readTree(readBounded(stream));
            if(root!=null && root.isObject()) {
                JsonNode errors=root.path("errors");
                if(errors.isArray()) {
                    for(var error:errors) {
                        var candidate=rejection(status,error);
                        if(candidate.getErrorCode()!=null) return candidate;
                    }
                } else if(errors.isObject()) return rejection(status,errors);
                return new LalamoveRequestException(status,root.path("message").asText(null),null);
            }
        } catch(IOException | RuntimeException ignored) {
            // A malformed/oversized error body must not erase the known HTTP rejection or expose its contents.
        }
        return new LalamoveRequestException(status,null,null);
    }
    private LalamoveRequestException rejection(int status,JsonNode error) {
        String field=null;
        var match=java.util.regex.Pattern.compile("'([^']{1,100})'").matcher(error.path("detail").asText());
        if(match.find()) field=match.group(1);
        return new LalamoveRequestException(status,error.path("id").asText(null),field);
    }

    private String readBounded(InputStream stream) throws IOException {
        if (stream == null) throw malformed();
        InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (body.length() + count > properties.getMaxResponseCharacters()) throw malformed();
            body.append(buffer, 0, count);
        }
        if (body.isEmpty()) throw malformed();
        return body.toString();
    }

    private JsonNode normalizeOrder(JsonNode data) {
        if (!data.isObject()) throw malformed();
        String id = identifier(data.path("orderId"));
        String status = data.path("status").asText();
        if (!STATUSES.contains(status)) throw malformed();
        ObjectNode result = mapper.createObjectNode();
        result.put("order_code", id).put("status", status).put("total_fee", fee(data));
        String shareLink = data.path("shareLink").asText();
        if (!shareLink.isBlank()) {
            if (!safeHttps(shareLink)) throw malformed();
            result.put("share_link", shareLink);
        }
        String driverId = data.path("driverId").asText();
        if (!driverId.isBlank()) result.put("driver_id", identifier(driverId));
        JsonNode stops = data.path("stops");
        if (stops.isArray() && stops.size() >= 2) {
            JsonNode pod = stops.get(stops.size() - 1).path("POD");
            if (Set.of("DELIVERED", "SIGNED").contains(pod.path("status").asText())) {
                String image = pod.path("image").asText();
                if (!image.isBlank() && safeHttps(image)) result.put("pod_url", image);
                if (pod.path("deliveredAt").isString()) {
                    try { result.put("delivered_at", Instant.parse(pod.path("deliveredAt").asText()).toString()); }
                    catch (RuntimeException error) { throw malformed(); }
                }
            }
        }
        result.set("raw", data);
        return result;
    }

    private void validateQuotation(JsonNode data) {
        if (data == null || !data.isObject()) throw malformed();
        identifier(data.path("quotationId"));
        quotationExpiry(data);
        fee(data);
        JsonNode stops = data.path("stops");
        if (!stops.isArray() || stops.size() != 2) throw malformed();
        identifier(stops.get(0).path("stopId"));
        identifier(stops.get(1).path("stopId"));
    }

    private Instant quotationExpiry(JsonNode data) {
        try {
            if (!data.path("expiresAt").isString()) throw malformed();
            return Instant.parse(data.path("expiresAt").asText());
        } catch (RuntimeException error) { throw malformed(); }
    }

    private BigDecimal fee(JsonNode data) {
        JsonNode breakdown = data.path("priceBreakdown");
        if (!"VND".equals(breakdown.path("currency").asText())) throw malformed();
        JsonNode total = breakdown.path("total");
        if (!total.isString() && !total.isNumber()) throw malformed();
        try {
            BigDecimal fee = new BigDecimal(total.asText());
            if (fee.signum() < 0 || fee.compareTo(MAX_FEE) > 0 || fee.stripTrailingZeros().scale() > 0) throw malformed();
            return fee;
        } catch (RuntimeException error) { throw malformed(); }
    }

    private Map<String, Object> stop(Map<String, Object> payload, String prefix) {
        String street = requiredString(payload.get(prefix + "_address"), "Pickup and delivery street addresses", 500);
        List<String> address = new ArrayList<>(); address.add(street);
        for (String suffix : List.of("ward_name", "district_name", "province_name")) {
            String part = string(payload.get(prefix + "_" + suffix));
            if (!part.isBlank()) address.add(part);
        }
        return Map.of("coordinates", Map.of("lat", coordinate(payload.get(prefix + "_latitude"), 90),
                "lng", coordinate(payload.get(prefix + "_longitude"), 180)), "address", String.join(", ", address));
    }

    private String coordinate(Object value, int limit) {
        try {
            if (value == null) throw new NumberFormatException();
            BigDecimal coordinate = new BigDecimal(string(value));
            if (coordinate.abs().compareTo(BigDecimal.valueOf(limit)) > 0 || coordinate.stripTrailingZeros().scale() > 15)
                throw new NumberFormatException();
            return coordinate.toPlainString();
        } catch (RuntimeException error) { throw new BadRequestException("Lalamove requires valid latitude and longitude for each stop"); }
    }

    private String phone(Object value, String field) {
        String number = requiredString(value, field, 20);
        if ("VN".equals(properties.getMarket())) {
            if (number.startsWith("0")) number = "+84" + number.substring(1);
            else if (number.startsWith("84")) number = "+" + number;
            if (!number.matches("\\+84[0-9]{9,10}")) throw new BadRequestException(field + " must be a valid Vietnamese number");
        } else if (!number.matches("\\+[1-9][0-9]{7,14}")) {
            throw new BadRequestException(field + " requires an international country code");
        }
        return number;
    }

    private String configuration() {
        if (!properties.isEnabled()) throw new ShippingProviderException("Lalamove shipping is disabled", 503);
        if (!properties.isEnabledConfigurationValid() || properties.getTimeoutMs() < 1000 || properties.getTimeoutMs() > 30000
                || properties.getMaxResponseCharacters() < 1 || properties.getMaxResponseCharacters() > 4 * 1024 * 1024
                || properties.getMarket() == null || !properties.getMarket().matches("[A-Z]{2}")
                || properties.getLanguage() == null || !properties.getLanguage().matches("[a-z]{2}_[A-Z]{2}"))
            throw new ShippingProviderException("Lalamove shipping configuration is invalid", 503);
        String base = properties.getBaseUrl();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base.endsWith("/v3") ? base.substring(0, base.length() - 3) : base;
    }

    private RestClient client() {
        RestClient current = restClient;
        if (current == null) synchronized (this) {
            current = restClient;
            if (current == null) restClient = current = builder.build();
        }
        return current;
    }

    private String requiredString(Object value, String field, int limit) {
        String text = string(value);
        if (text.isBlank() || text.length() > limit) throw new BadRequestException(field + " is required and must fit the carrier limit");
        return text;
    }
    private String string(Object value) { return value == null ? "" : value instanceof JsonNode node ? node.asText() : value.toString(); }
    private String identifier(JsonNode value) {
        if (!value.isString()) throw malformed();
        return identifier(value.asText());
    }
    private String identifier(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,100}")) throw malformed();
        return value;
    }
    private boolean safeHttps(String value) {
        try { URI uri = URI.create(value); return "https".equals(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null; }
        catch (IllegalArgumentException error) { return false; }
    }
    private ShippingProviderException malformed() { return new ShippingProviderException("Lalamove returned an invalid response"); }
    private boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause())
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) return true;
        return false;
    }
}
