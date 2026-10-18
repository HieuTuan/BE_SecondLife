package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.integration.shipping.LalamoveWebhookVerifier;

import com.secondlife.secondlife.config.LalamoveProperties;
import com.secondlife.secondlife.controller.LalamoveWebhookController;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class LalamoveWebhookVerifierTest {
    private static final String KEY = "pk_test_webhook-unit";
    private static final String SECRET = "webhook-test-secret";
    private static final String PATH = "/api/v1/shipping/lalamove/callback";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final LalamoveProperties properties = properties();
    private final LalamoveWebhookVerifier verifier = new LalamoveWebhookVerifier(properties, mapper);

    @Test
    void emptyProviderConnectionProbeDoesNotBecomeAnEvent() {
        assertNull(verifier.verifyAndNormalize(null));
        assertNull(verifier.verifyAndNormalize(""));
        assertNull(verifier.verifyAndNormalize(" \n\t "));
    }

    @Test
    void setupControllerAcceptsAnEmptyProbeWithoutAccessingShipmentState() {
        LalamoveWebhookController controller = new LalamoveWebhookController(verifier, null);
        assertTrue(controller.callback(null, null).success());
        assertTrue(controller.callback("", null).success());
        assertTrue(controller.callback(signed("FUTURE_EVENT", "{}"), "application/json; charset=UTF-8").success());
    }

    @Test
    void nonemptyControllerPayloadRequiresJsonContentTypeBeforeAccessingShipmentState() {
        LalamoveWebhookController controller = new LalamoveWebhookController(verifier, null);
        assertThrows(BadRequestException.class, () -> controller.callback("{}", null));
        assertThrows(BadRequestException.class, () -> controller.callback("{}", "text/plain"));
        assertThrows(BadRequestException.class, () -> controller.callback("{}", "invalid/content/type"));
    }

    @Test
    void verifiesIndependentHmacVectorWithUnicodeAndTheOfficialDataOnlySigningFormat() {
        String body = """
                {"apiKey":"pk_test_webhook-unit","timestamp":1775056380,
                 "signature":"0fab7f2000d120f3ab8d67ba8e0d69c7755bc52fc733e1fccf48383b0261780f",
                 "eventId":"84e3f1db-87fa-4bf3-a224-5832e1862a50","eventType":"ORDER_STATUS_CHANGED",
                 "eventVersion":"v3","data":{"order":{"orderId":"3463513590991397204",
                 "status":"ON_GOING","remarks":"Giao tại nhà Nguyễn"},"updatedAt":"2026-04-01T15:13:00Z"}}
                """;
        JsonNode result = verifier.verifyAndNormalize(body);
        assertEquals("3463513590991397204", result.path("order_code").asText());
        assertEquals("ON_GOING", result.path("status").asText());
        assertEquals("2026-04-01T15:13:00Z", result.path("occurred_at").asText());
    }

    @Test
    void acceptsDelayedRetriesAndMillisecondTimestampsWithoutRewritingSignedTimestamp() {
        String data = "{\"order\":{\"orderId\":\"3463513590991397204\",\"status\":\"PICKED_UP\"},\"updatedAt\":\"2026-04-01T15:13:00Z\"}";
        assertNotNull(verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", data, "1775056380000", PATH)));
        assertNotNull(verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", data, "1775056380", PATH)));
    }

    @Test
    void rejectsTamperedDataWrongKeySignatureAndCallbackPath() {
        String signed = signed("ORDER_STATUS_CHANGED", statusData("ON_GOING"));
        assertThrows(ForbiddenException.class, () -> verifier.verifyAndNormalize(signed.replace("ON_GOING", "COMPLETED")));
        assertThrows(ForbiddenException.class, () -> verifier.verifyAndNormalize(signed.replace(KEY, "pk_test_another-account")));
        ObjectNode changed = (ObjectNode) mapper.readTree(signed);
        changed.put("signature", "f".repeat(64));
        assertThrows(ForbiddenException.class, () -> verifier.verifyAndNormalize(changed.toString()));
        assertThrows(ForbiddenException.class, () -> verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", statusData("ON_GOING"), "1775056380", "/api/v1/shipping/callback")));
    }

    @Test
    void disabledIntegrationCannotAcceptSignedStateChanges() {
        properties.setEnabled(false);
        assertThrows(ForbiddenException.class, () -> verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", statusData("ON_GOING"))));
        assertNull(verifier.verifyAndNormalize(""));
    }

    @Test
    void rejectsWrongVersionMalformedEnvelopeAndMissingAuthoritativeEventTime() {
        assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize("[]"));
        assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize("{broken"));
        assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", statusData("ON_GOING")).replace("\"v3\"", "\"v2\"")));
        assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", "{\"order\":{\"orderId\":\"1234\",\"status\":\"ON_GOING\"}}")));
        assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", statusData("invented_status"))));
    }

    @Test
    void rejectsFutureSignedTimestampOrEventTimeAndOversizedBodies() {
        String future = String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond());
        assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", statusData("ON_GOING"), future, PATH)));
        String futureData = statusData("ON_GOING").replace("2026-04-01T15:13:00Z", Instant.now().plusSeconds(3600).toString());
        assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", futureData)));
        properties.setMaxResponseCharacters(30);
        assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", statusData("ON_GOING"))));
    }

    @Test
    void preservesKeyOrderDuringSigningRatherThanSortingTheData() {
        String data = "{\"updatedAt\":\"2026-04-01T15:13:00Z\",\"order\":{\"status\":\"ON_GOING\",\"orderId\":\"3463513590991397204\"}}";
        assertNotNull(verifier.verifyAndNormalize(signed("ORDER_STATUS_CHANGED", data)));
        String original = signed("ORDER_STATUS_CHANGED", statusData("ON_GOING"));
        ObjectNode envelope = (ObjectNode) mapper.readTree(original);
        envelope.set("data", mapper.readTree(data));
        assertThrows(ForbiddenException.class, () -> verifier.verifyAndNormalize(envelope.toString()));
    }

    @Test
    void verifiesButIgnoresUnrelatedSignedEventsWithoutTouchingWallets() {
        assertNull(verifier.verifyAndNormalize(signed("WALLET_BALANCE_CHANGED", "{\"balance\":{\"amount\":\"999999\",\"currency\":\"VND\"}}")));
        assertNull(verifier.verifyAndNormalize(signed("FUTURE_EVENT", "{}")));
    }

    @Test
    void parsesOfficialAmountChangedPriceObjectRatherThanWalletBalance() {
        String data = """
                {"balance":{"amount":"99802","currency":"VND"},"order":{"orderId":"3463486036435772011",
                 "price":{"totalPrice":"104000","priorityFee":"10000","currency":"VND","subTotal":"94000"},
                 "status":"ASSIGNING_DRIVER"},"updatedAt":"2026-04-01T15:43:00Z"}
                """;
        JsonNode result = verifier.verifyAndNormalize(signed("ORDER_AMOUNT_CHANGED", data));
        assertEquals(104000, result.path("fee").decimalValue().intValueExact());
    }

    @Test
    void rejectsForeignCurrencyNegativeOrFractionalVndFee() {
        for (String price : new String[]{"{\"totalPrice\":\"104\",\"currency\":\"HKD\"}",
                "{\"totalPrice\":\"-1\",\"currency\":\"VND\"}", "{\"totalPrice\":\"1.5\",\"currency\":\"VND\"}"}) {
            String data = "{\"order\":{\"orderId\":\"1234\",\"price\":" + price + "},\"updatedAt\":\"2026-04-01T15:43:00Z\"}";
            assertThrows(BadRequestException.class, () -> verifier.verifyAndNormalize(signed("ORDER_AMOUNT_CHANGED", data)));
        }
    }

    @Test
    void normalizesDriverAssignmentWithoutInventingAnOrderStatus() {
        String data = """
                {"driver":{"driverId":"79973","phone":"+84987654321","name":"Driver"},
                "location":{"lng":106.72,"lat":10.81},"order":{"orderId":"3463513590991397204"},
                "updatedAt":"2026-04-01T15:13:00Z"}
                """;
        JsonNode result = verifier.verifyAndNormalize(signed("DRIVER_ASSIGNED", data));
        assertEquals("79973", result.path("driver_id").asText());
        assertFalse(result.has("status"));
    }

    @Test
    void destinationPodDoesNotInventCompletedOrderAndIgnoresPickupStopEvidence() {
        String data = """
                {"order":{"orderId":"3463513590991397508","status":"PICKED_UP","stops":[
                  {"POD":{"status":"SIGNED","image":"https://example.com/pickup.png"}},
                  {"POD":{"status":"DELIVERED","image":"https://example.com/delivery.png","deliveredAt":"2026-04-01T16:19:00Z"}}
                ]},"updatedAt":"2026-04-01T16:19:00Z"}
                """;
        JsonNode result = verifier.verifyAndNormalize(signed("POD_STATUS_CHANGED", data));
        assertEquals("PICKED_UP", result.path("status").asText());
        assertEquals("DELIVERED", result.path("pod_status").asText());
        assertEquals("https://example.com/delivery.png", result.path("pod_url").asText());
    }

    @Test
    void normalizesCancelAndCloneUsingOldOrderForLookupAndKeepsNewOrderSeparate() {
        String data = """
                {"order":{"orderId":"3463513590991397328"},"prevOrderId":"3463513590991397305",
                 "updatedAt":"2026-04-01T15:39:00Z"}
                """;
        JsonNode result = verifier.verifyAndNormalize(signed("ORDER_REPLACED", data));
        assertEquals("3463513590991397305", result.path("order_code").asText());
        assertEquals("3463513590991397328", result.path("replacement_order_code").asText());
        assertFalse(result.has("status"));
    }

    private String statusData(String status) {
        return "{\"order\":{\"orderId\":\"3463513590991397204\",\"status\":\"" + status + "\"},\"updatedAt\":\"2026-04-01T15:13:00Z\"}";
    }

    private String signed(String type, String data) { return signed(type, data, "1775056380", PATH); }

    private String signed(String type, String data, String timestamp, String path) {
        JsonNode parsed = mapper.readTree(data);
        String compact = mapper.writeValueAsString(parsed);
        ObjectNode envelope = mapper.createObjectNode();
        envelope.put("apiKey", KEY);
        envelope.put("timestamp", Long.parseLong(timestamp));
        envelope.put("signature", independentSignature(timestamp, path, compact));
        envelope.put("eventId", "84e3f1db-87fa-4bf3-a224-5832e1862a50");
        envelope.put("eventType", type);
        envelope.put("eventVersion", "v3");
        envelope.set("data", parsed);
        return mapper.writeValueAsString(envelope);
    }

    private String independentSignature(String timestamp, String path, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "\r\nPOST\r\n" + path + "\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new AssertionError(ex); }
    }

    private static LalamoveProperties properties() {
        LalamoveProperties result = new LalamoveProperties();
        result.setEnabled(true);
        result.setKey(KEY);
        result.setSecret(SECRET);
        result.setMaxResponseCharacters(100000);
        result.setWebhookMaxFutureSkewSeconds(300);
        return result;
    }
}
