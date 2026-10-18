package com.secondlife.secondlife.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.secondlife.secondlife.config.LalamoveProperties;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.integration.shipping.LalamoveShippingProvider;
import com.secondlife.secondlife.integration.shipping.LalamoveSignatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LalamoveShippingProviderTest {
    private static final String BASE = "https://rest.sandbox.lalamove.com";
    private static final String KEY = "pk_test_transport-key";
    private static final String SECRET = "sk_test_private-transport-secret";
    private static final String ORDER = "1234567890123456789";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test void quotationUsesCoordinatesAndPreservesNativeQuotationAndVndAmount() {
        Module module = module(properties());
        JsonNode nativeQuote = quotation();
        expect(module, HttpMethod.POST, "/v3/quotations")
                .andExpect(request -> {
                    JsonNode data = requestBody(request).path("data");
                    assertEquals("MOTORCYCLE", data.path("serviceType").asText());
                    assertEquals("vi_VN", data.path("language").asText());
                    assertEquals("10.762622", data.path("stops").get(0).path("coordinates").path("lat").asText());
                    assertEquals("106.660172", data.path("stops").get(0).path("coordinates").path("lng").asText());
                    assertEquals("123 Đường A, Phường A, Hồ Chí Minh", data.path("stops").get(0).path("address").asText());
                    assertFalse(data.has("item"));
                    assertFalse(data.has("specialRequests"));
                    assertFalse(data.has("cod_amount"));
                }).andRespond(withSuccess(envelope(nativeQuote), MediaType.APPLICATION_JSON));
        JsonNode result = module.provider().preview(payload());
        assertEquals(0, new BigDecimal("25000").compareTo(result.path("total_fee").decimalValue()));
        assertEquals(nativeQuote, result.path("provider_quote"));
        assertEquals(nativeQuote.path("expiresAt"), result.path("expires_at"));
        module.server().verify();
    }

    @ParameterizedTest @ValueSource(strings={"from_phone", "to_phone"})
    void invalidVietnameseContactIsRejectedBeforeRequestingAQuotation(String field) {
        Module module=module(properties());
        var route=payload();route.put(field,"123456789012345");
        var error=assertThrows(BadRequestException.class,()->module.provider().preview(route));
        assertTrue(error.getMessage().contains(field.equals("from_phone")?"Seller pickup":"Buyer delivery"));
        assertFalse(error.getMessage().contains("123456789012345"));
        module.server().verify();
    }

    @Test void invalidRecipientOnLegacyPaidQuoteIdentifiesTheContactWithoutCreatingAnOrder() {
        Module module=module(properties());var route=payload();route.put("to_phone","123456789012345");
        var error=assertThrows(BadRequestException.class,()->module.provider().create(route));
        assertEquals("Buyer delivery phone number must be a valid Vietnamese number",error.getMessage());
        module.server().verify();
    }

    @Test void createsWithCachedQuotationStopIdsPhonesPodAndStableMetadata() {
        Module module = module(properties());
        expect(module, HttpMethod.POST, "/v3/orders")
                .andExpect(request -> {
                    JsonNode data = requestBody(request).path("data");
                    assertEquals("quotation-1", data.path("quotationId").asText());
                    assertEquals("stop-from", data.path("sender").path("stopId").asText());
                    assertEquals("+84901234567", data.path("sender").path("phone").asText());
                    assertEquals("Seller", data.path("sender").path("name").asText());
                    assertEquals("stop-to", data.path("recipients").get(0).path("stopId").asText());
                    assertEquals("+84909876543", data.path("recipients").get(0).path("phone").asText());
                    assertEquals("private-recipient-marker", data.path("recipients").get(0).path("name").asText());
                    assertEquals("SecondLife item", data.path("recipients").get(0).path("remarks").asText());
                    assertTrue(data.path("isPODEnabled").asBoolean());
                    assertEquals("SL-request-1", data.path("metadata").path("secondlifeClientOrderCode").asText());
                }).andRespond(withSuccess(envelope(order("ASSIGNING_DRIVER")), MediaType.APPLICATION_JSON));
        JsonNode result = module.provider().create(payload());
        assertEquals(ORDER, result.path("order_code").asText());
        assertEquals("ASSIGNING_DRIVER", result.path("status").asText());
        assertEquals("https://share.lalamove.com/order", result.path("share_link").asText());
        assertEquals(order("ASSIGNING_DRIVER"), result.path("raw"));
        module.server().verify();
    }
    @Test void refreshedQuotationPreservesPaidVehicleWithoutAutoSelection() {
        Module module=module(properties());var route=payload();
        ((tools.jackson.databind.node.ObjectNode)route.get("provider_quote")).put("serviceType","CAR");
        var refreshed=(tools.jackson.databind.node.ObjectNode)quotation();refreshed.put("serviceType","CAR");
        expect(module,HttpMethod.POST,"/v3/quotations")
            .andExpect(request->assertEquals("CAR",requestBody(request).path("data").path("serviceType").asText()))
            .andRespond(withSuccess(envelope(refreshed),MediaType.APPLICATION_JSON));
        module.provider().preview(route);module.server().verify();
    }

    @Test void expiredOrMissingQuotationCannotCreateOrSilentlyRefreshAnOrder() {
        Module module = module(properties());
        Map<String, Object> payload = payload();
        JsonNode expired = quotation().deepCopy();
        ((tools.jackson.databind.node.ObjectNode) expired).put("expiresAt", Instant.now().minusSeconds(1).toString());
        payload.put("provider_quote", expired);
        assertThrows(ConflictException.class, () -> module.provider().create(payload));
        payload.remove("provider_quote");
        assertThrows(ConflictException.class, () -> module.provider().create(payload));
        module.server().verify();
    }

    @Test void detailReadsRecipientPodAndDriverAndUsesEmptySignedGetBody() {
        Module module = module(properties());
        JsonNode nativeOrder = order("COMPLETED");
        expect(module, HttpMethod.GET, "/v3/orders/" + ORDER).andRespond(withSuccess(envelope(nativeOrder), MediaType.APPLICATION_JSON));
        JsonNode detail = module.provider().detail(ORDER);
        assertEquals("https://images.lalamove.com/pod.jpg", detail.path("pod_url").asText());
        assertEquals("2026-10-12T09:00:00Z", detail.path("delivered_at").asText());
        assertEquals("driver-1", detail.path("driver_id").asText());
        module.server().verify();
    }

    @Test void successfulEmpty204CancellationIsExplicitlyConfirmed() {
        Module module = module(properties());
        expect(module, HttpMethod.DELETE, "/v3/orders/" + ORDER).andRespond(withNoContent());
        JsonNode result = module.provider().cancel(ORDER);
        assertEquals(ORDER, result.path("order_code").asText());
        assertEquals("CANCELED", result.path("status").asText());
        module.server().verify();
    }

    @Test void citiesAndDriverExposeUnwrappedNativeData() {
        Module module = module(properties());
        expect(module, HttpMethod.GET, "/v3/cities").andRespond(withSuccess(envelope(MAPPER.valueToTree(List.of(Map.of("name", "Ho Chi Minh")))), MediaType.APPLICATION_JSON));
        expect(module, HttpMethod.GET, "/v3/orders/" + ORDER + "/drivers/driver-1")
                .andRespond(withSuccess(envelope(MAPPER.valueToTree(Map.of("driverId", "driver-1"))), MediaType.APPLICATION_JSON));
        assertTrue(module.provider().cities().isArray());
        assertEquals("driver-1", module.provider().driver(ORDER, "driver-1").path("driverId").asText());
        module.server().verify();
    }

    @Test void emptySuccessful200ResponseDoesNotConfirmCancellation() {
        Module module = module(properties());
        expect(module, HttpMethod.DELETE, "/v3/orders/" + ORDER).andRespond(withSuccess("{\"data\":{}}", MediaType.APPLICATION_JSON));
        assertThrows(ShippingProviderException.class, () -> module.provider().cancel(ORDER));
        module.server().verify();
    }

    @Test void missingCoordinatesAndUnsafeIdentifiersAreRejectedBeforeRequests() {
        Module module = module(properties());
        Map<String, Object> route = payload(); route.remove("from_latitude");
        assertThrows(BadRequestException.class, () -> module.provider().preview(route));
        assertThrows(ShippingProviderException.class, () -> module.provider().detail("../orders/private"));
        module.server().verify();
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "NaN", "25000VND", "25000.5", "1000000001"})
    void invalidProviderAmountsAreRejected(String price) {
        Module module = module(properties());
        var quote = (tools.jackson.databind.node.ObjectNode) quotation();
        ((tools.jackson.databind.node.ObjectNode) quote.path("priceBreakdown")).put("total", price);
        expect(module, HttpMethod.POST, "/v3/quotations").andRespond(withSuccess(envelope(quote), MediaType.APPLICATION_JSON));
        assertThrows(ShippingProviderException.class, () -> module.provider().preview(payload()));
        module.server().verify();
    }

    @Test void foreignCurrencyCannotEnterVndCheckout() {
        Module module = module(properties());
        var quote = (tools.jackson.databind.node.ObjectNode) quotation();
        ((tools.jackson.databind.node.ObjectNode) quote.path("priceBreakdown")).put("currency", "USD");
        expect(module, HttpMethod.POST, "/v3/quotations").andRespond(withSuccess(envelope(quote), MediaType.APPLICATION_JSON));
        assertThrows(ShippingProviderException.class, () -> module.provider().preview(payload()));
        module.server().verify();
    }

    @Test void createTimeoutIsSanitizedAndIsNotRetried() {
        Module module = module(properties());
        expect(module, HttpMethod.POST, "/v3/orders").andRespond(request -> { throw new ResourceAccessException(SECRET, new SocketTimeoutException()); });
        ShippingProviderException error = assertThrows(ShippingProviderException.class, () -> module.provider().create(payload()));
        assertEquals(504, error.getUpstreamStatus());
        assertFalse(error.getMessage().contains(SECRET));
        assertNull(error.getCause());
        module.server().verify();
    }

    @Test void httpErrorsAndOversizedResponsesNeverExposeUpstreamBody() {
        Module module = module(properties());
        expect(module, HttpMethod.POST, "/v3/orders").andRespond(withStatus(HttpStatus.BAD_GATEWAY).body(SECRET + " private-recipient-marker"));
        ShippingProviderException error = assertThrows(ShippingProviderException.class, () -> module.provider().create(payload()));
        assertFalse(error.getMessage().contains(SECRET));
        assertFalse(error.getMessage().contains("private-recipient-marker"));
        assertFalse(error.getMessage().contains(KEY));
        assertNull(error.getCause());
        module.server().verify();
        LalamoveProperties properties = properties();
        properties.setMaxResponseCharacters(30);
        Module bounded = module(properties);
        expect(bounded, HttpMethod.GET, "/v3/cities").andRespond(withSuccess("{\"data\":[]}" + " ".repeat(31), MediaType.APPLICATION_JSON));
        assertThrows(ShippingProviderException.class, bounded.provider()::cities);
        bounded.server().verify();
    }

    @ParameterizedTest @ValueSource(strings={
        "{\"errors\":[{\"id\":\"ERR_INVALID_FIELD\",\"message\":\"private-sensitive-marker\",\"detail\":\"'sender.phone' validation failed: private-sensitive-marker\"}]}",
        "{\"errors\":{\"id\":\"ERR_INVALID_FIELD\",\"detail\":\"'sender.phone' validation failed: private-sensitive-marker\"}}"})
    void knownRejectionIncludesHttpCodeErrorIdAndSafeFieldWithoutUpstreamValues(String body) {
        Module module=module(properties());
        expect(module,HttpMethod.POST,"/v3/orders").andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(body));
        var error=assertThrows(ShippingProviderException.class,()->module.provider().create(payload()));
        assertEquals(422,error.getUpstreamStatus());assertTrue(error.getMessage().contains("HTTP 422"));
        assertTrue(error.getMessage().contains("ERR_INVALID_FIELD"));assertTrue(error.getMessage().contains("sender.phone"));
        assertFalse(error.getMessage().contains("private-sensitive-marker"));assertNull(error.getCause());module.server().verify();
    }
    @Test void insufficientCreditRootMessageIsRecognizedWithoutExposingBody() {
        Module module=module(properties());
        expect(module,HttpMethod.POST,"/v3/orders").andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
            .body("{\"message\":\"ERR_INSUFFICIENT_CREDIT\",\"detail\":\"private-sensitive-marker\"}"));
        var error=assertThrows(ShippingProviderException.class,()->module.provider().create(payload()));
        assertEquals(402,error.getUpstreamStatus());assertTrue(error.getMessage().contains("ERR_INSUFFICIENT_CREDIT"));
        assertFalse(error.getMessage().contains("private-sensitive-marker"));module.server().verify();
    }
    @ParameterizedTest @ValueSource(strings={"invalid-json","{\"errors\":[{\"id\":\"ERR_PRIVATE_SENSITIVE_MARKER\"}]}","{\"message\":\"private-sensitive-marker\"}"})
    void unrecognizedOrMalformedErrorKeepsKnownHttpFailureAndNoRawText(String body) {
        Module module=module(properties());
        expect(module,HttpMethod.POST,"/v3/orders").andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).body(body));
        var error=assertThrows(ShippingProviderException.class,()->module.provider().create(payload()));
        assertEquals(422,error.getUpstreamStatus());assertTrue(error.getMessage().contains("HTTP 422"));
        assertFalse(error.getMessage().toLowerCase().contains("sensitive"));module.server().verify();
    }

    @Test @ExtendWith(OutputCaptureExtension.class)
    void debugLogsNeverExposeRecipientOrCredentials(CapturedOutput output) {
        Module module = module(properties());
        expect(module, HttpMethod.POST, "/v3/orders").andRespond(withSuccess(envelope(order("ASSIGNING_DRIVER")), MediaType.APPLICATION_JSON));
        Logger logger = (Logger) LoggerFactory.getLogger("org.springframework.web.client.DefaultRestClient");
        Level previous = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);
            module.provider().create(payload());
            assertFalse(output.getAll().contains("private-recipient-marker"));
            assertFalse(output.getAll().contains(KEY));
            assertFalse(output.getAll().contains(SECRET));
        } finally { logger.setLevel(previous); }
        module.server().verify();
    }

    @ParameterizedTest @ValueSource(strings = {"http://rest.lalamove.com", "https://rest.lalamove.com.evil.example", "https://user@rest.lalamove.com", "https://rest.lalamove.com/v4", "https://rest.lalamove.com?secret=value"})
    void unapprovedGatewayIsRejectedBeforeSigningOrSending(String base) {
        LalamoveProperties properties = properties(); properties.setBaseUrl(base);
        Module module = module(properties);
        assertThrows(ShippingProviderException.class, module.provider()::cities);
        module.server().verify();
    }

    @Test void disabledAndEnvironmentMismatchedCredentialsFailWithoutRequests() {
        LalamoveProperties disabled = properties(); disabled.setEnabled(false);
        Module off = module(disabled);
        assertThrows(ShippingProviderException.class, off.provider()::cities);
        off.server().verify();
        LalamoveProperties mismatched = properties(); mismatched.setBaseUrl("https://rest.lalamove.com");
        Module wrong = module(mismatched);
        assertThrows(ShippingProviderException.class, wrong.provider()::cities);
        wrong.server().verify();
    }

    @Test void redirectsAndUnsupportedGhnOperationsAreRejected() {
        Module module = module(properties());
        expect(module, HttpMethod.GET, "/v3/cities").andRespond(withStatus(HttpStatus.FOUND).header("Location", "https://untrusted.example"));
        assertThrows(ShippingProviderException.class, module.provider()::cities);
        assertThrows(ConflictException.class, () -> module.provider().label(ORDER));
        assertThrows(ConflictException.class, () -> module.provider().returnToSender(ORDER));
        module.server().verify();
    }

    private static ResponseActions expect(Module module, HttpMethod method, String path) {
        return module.server().expect(requestTo(BASE + path)).andExpect(method(method)).andExpect(header("Market", "VN"))
                .andExpect(request -> {
                    String authorization = request.getHeaders().getFirst("Authorization");
                    assertNotNull(authorization);
                    String[] pieces = authorization.substring("hmac ".length()).split(":");
                    assertEquals(KEY, pieces[0]);
                    assertTrue(pieces[1].matches("[0-9]+"));
                    String exactBody = ((MockClientHttpRequest) request).getBodyAsString();
                    assertEquals(LalamoveSignatures.sign(SECRET, pieces[1], method.name(), path, exactBody), pieces[2]);
                    assertDoesNotThrow(() -> UUID.fromString(request.getHeaders().getFirst("Request-ID")));
                });
    }

    private static JsonNode requestBody(org.springframework.http.client.ClientHttpRequest request) {
        return MAPPER.readTree(((MockClientHttpRequest) request).getBodyAsString());
    }

    private static String envelope(JsonNode data) { return MAPPER.writeValueAsString(Map.of("data", data)); }

    private static JsonNode quotation() {
        return MAPPER.valueToTree(Map.of("quotationId", "quotation-1", "serviceType", "MOTORCYCLE", "expiresAt", Instant.now().plusSeconds(300).toString(),
                "priceBreakdown", Map.of("total", "25000", "currency", "VND"), "stops", List.of(Map.of("stopId", "stop-from"), Map.of("stopId", "stop-to"))));
    }

    private static JsonNode order(String status) {
        return MAPPER.valueToTree(Map.of("orderId", ORDER, "status", status, "priceBreakdown", Map.of("total", "25000", "currency", "VND"),
                "shareLink", "https://share.lalamove.com/order", "driverId", "driver-1", "stops", List.of(Map.of("stopId", "stop-from"),
                        Map.of("stopId", "stop-to", "POD", Map.of("status", "DELIVERED", "image", "https://images.lalamove.com/pod.jpg", "deliveredAt", "2026-10-12T09:00:00Z")))));
    }

    private static Map<String, Object> payload() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("from_name", "Seller"); map.put("from_phone", "0901234567"); map.put("from_address", "123 Đường A");
        map.put("from_ward_name", "Phường A"); map.put("from_province_name", "Hồ Chí Minh");
        map.put("from_latitude", new BigDecimal("10.762622")); map.put("from_longitude", new BigDecimal("106.660172"));
        map.put("to_name", "private-recipient-marker"); map.put("to_phone", "+84909876543"); map.put("to_address", "456 Đường B");
        map.put("to_ward_name", "Phường B"); map.put("to_province_name", "Hồ Chí Minh");
        map.put("to_latitude", new BigDecimal("10.776889")); map.put("to_longitude", new BigDecimal("106.700806"));
        map.put("content", "SecondLife item"); map.put("client_order_code", "SL-request-1"); map.put("provider_quote", quotation());
        return map;
    }

    private static LalamoveProperties properties() {
        LalamoveProperties p = new LalamoveProperties(); p.setEnabled(true); p.setBaseUrl(BASE);
        p.setKey(KEY); p.setSecret(SECRET); p.setMarket("VN"); p.setLanguage("vi_VN");
        p.setTimeoutMs(5000); p.setMaxResponseCharacters(4 * 1024 * 1024); p.setWebhookMaxFutureSkewSeconds(300);
        return p;
    }

    private static Module module(LalamoveProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        LalamoveShippingProvider provider = new LalamoveShippingProvider(builder, MAPPER, properties);
        return new Module(provider, MockRestServiceServer.bindTo(builder).build());
    }
    private record Module(LalamoveShippingProvider provider, MockRestServiceServer server) { }
}
