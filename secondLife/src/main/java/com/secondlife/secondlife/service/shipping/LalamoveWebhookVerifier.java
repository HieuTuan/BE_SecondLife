package com.secondlife.secondlife.service.shipping;

import com.secondlife.secondlife.config.LalamoveProperties;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ForbiddenException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.net.URI;
import java.security.MessageDigest;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;

@Component
public class LalamoveWebhookVerifier {
    public static final String CALLBACK_PATH = "/api/v1/shipping/lalamove/callback";
    private static final Set<String> SUPPORTED_EVENTS = Set.of("ORDER_STATUS_CHANGED", "ORDER_CREATED",
            "ORDER_AMOUNT_CHANGED", "DRIVER_ASSIGNED", "POD_STATUS_CHANGED", "ORDER_REPLACED");
    private static final Set<String> STATUSES = Set.of("ASSIGNING_DRIVER", "ON_GOING", "PICKED_UP",
            "COMPLETED", "CANCELED", "REJECTED", "EXPIRED");
    private static final Set<String> POD_STATUSES = Set.of("PENDING", "DELIVERED", "SIGNED", "FAILED");
    private final LalamoveProperties properties;
    private final ObjectMapper mapper;

    public LalamoveWebhookVerifier(LalamoveProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    /** Returns null only for the empty setup probe or a verified event that does not update shipments. */
    public JsonNode verifyAndNormalize(String body) {
        if (body == null || body.isEmpty()) return null;
        if (body.length() > properties.getMaxResponseCharacters())
            throw new BadRequestException("Lalamove webhook exceeds the configured payload limit");
        if (body.isBlank()) return null;
        if (!properties.isEnabled() || properties.getKey() == null || properties.getKey().isBlank()
                || properties.getSecret() == null || properties.getSecret().isBlank())
            throw new ForbiddenException("Lalamove webhook authentication failed");

        JsonNode envelope;
        try { envelope = mapper.readTree(body); }
        catch (JacksonException ex) { throw new BadRequestException("Lalamove webhook must contain valid JSON"); }
        if (envelope == null || !envelope.isObject()) throw invalid("envelope");
        String apiKey = text(envelope, "apiKey", 300);
        String signature = text(envelope, "signature", 64);
        if (!constantTimeText(properties.getKey(), apiKey) || !signature.matches("[0-9a-fA-F]{64}"))
            throw new ForbiddenException("Lalamove webhook authentication failed");
        if (!"v3".equals(text(envelope, "eventVersion", 10))) throw invalid("eventVersion");
        String eventId = text(envelope, "eventId", 100);
        String eventType = text(envelope, "eventType", 100);
        JsonNode data = envelope.get("data");
        if (data == null || !data.isObject()) throw invalid("data");
        JsonNode timestampNode = envelope.get("timestamp");
        if (timestampNode == null || !(timestampNode.isIntegralNumber() || timestampNode.isTextual()))
            throw invalid("timestamp");
        String timestamp = timestampNode.asText();
        if (!timestamp.matches("[0-9]{1,13}")) throw invalid("timestamp");
        String expected = LalamoveSignatures.sign(properties.getSecret(), timestamp, "POST", CALLBACK_PATH,
                mapper.writeValueAsString(data));
        if (!MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(signature)))
            throw new ForbiddenException("Lalamove webhook authentication failed");
        Instant signedAt;
        try {
            long value = Long.parseLong(timestamp);
            if (value <= 0) throw invalid("timestamp");
            signedAt = timestamp.length() > 10 ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
        } catch (NumberFormatException | DateTimeException ex) { throw invalid("timestamp"); }
        rejectFuture(signedAt, "timestamp");
        // Retries can arrive a day later; signing time is not the shipment's chronological event time.
        if (!SUPPORTED_EVENTS.contains(eventType)) return null;

        Instant occurredAt;
        try { occurredAt = Instant.parse(text(data, "updatedAt", 60)); }
        catch (DateTimeException ex) { throw invalid("updatedAt"); }
        rejectFuture(occurredAt, "updatedAt");
        JsonNode order = data.get("order");
        if (order == null || !order.isObject()) throw invalid("order");
        String newOrderCode = orderCode(order, "orderId");
        ObjectNode result = mapper.createObjectNode();
        result.put("event_id", eventId);
        result.put("event_type", eventType);
        result.put("occurred_at", occurredAt.toString());
        if ("ORDER_REPLACED".equals(eventType)) {
            result.put("order_code", orderCode(data, "prevOrderId"));
            result.put("replacement_order_code", newOrderCode);
        } else result.put("order_code", newOrderCode);

        if (order.hasNonNull("status")) {
            String status = text(order, "status", 30);
            if (!STATUSES.contains(status)) throw invalid("status");
            result.put("status", status);
        } else if (Set.of("ORDER_STATUS_CHANGED", "ORDER_CREATED").contains(eventType)) throw invalid("status");

        JsonNode price = order.get("priceBreakdown");
        String priceField = "total";
        if (price == null || price.isNull()) { price = order.get("price"); priceField = "totalPrice"; }
        if (price != null && !price.isNull()) result.put("fee", fee(price, priceField));
        else if ("ORDER_AMOUNT_CHANGED".equals(eventType)) throw invalid("price");
        JsonNode driver = data.get("driver");
        if (driver != null && driver.hasNonNull("driverId")) result.put("driver_id", text(driver, "driverId", 100));
        else if (order.hasNonNull("driverId") && !order.path("driverId").asText().isBlank())
            result.put("driver_id", text(order, "driverId", 100));
        if (order.hasNonNull("shareLink") && !order.path("shareLink").asText().isBlank())
            result.put("share_link", webUrl(order, "shareLink"));
        JsonNode stops = order.get("stops");
        if (stops != null && stops.isArray() && stops.size() >= 2) {
            JsonNode pod = stops.get(stops.size() - 1).get("POD");
            if (pod != null && !pod.isNull()) {
                String podStatus = text(pod, "status", 30);
                if (!POD_STATUSES.contains(podStatus)) throw invalid("POD.status");
                result.put("pod_status", podStatus);
                if (pod.hasNonNull("image") && !pod.path("image").asText().isBlank())
                    result.put("pod_url", webUrl(pod, "image"));
                if (pod.hasNonNull("deliveredAt") && !pod.path("deliveredAt").asText().isBlank()) {
                    try {
                        Instant deliveredAt = Instant.parse(text(pod, "deliveredAt", 60));
                        rejectFuture(deliveredAt, "POD.deliveredAt");
                        result.put("pod_delivered_at", deliveredAt.toString());
                    } catch (DateTimeException ex) { throw invalid("POD.deliveredAt"); }
                }
            }
        }
        // Persist useful signed event data without storing the API credential or signature in the audit payload.
        ObjectNode audit = ((ObjectNode) envelope).deepCopy();
        audit.remove("apiKey");
        audit.remove("signature");
        result.set("raw", audit);
        return result;
    }

    private BigDecimal fee(JsonNode price, String field) {
        if (!price.isObject() || !"VND".equals(text(price, "currency", 3))) throw invalid("price.currency");
        JsonNode amount = price.get(field);
        if (amount == null || !(amount.isTextual() || amount.isNumber())) throw invalid("price." + field);
        try {
            BigDecimal value = new BigDecimal(amount.asText());
            if (value.signum() < 0 || value.compareTo(new BigDecimal("1000000000")) > 0
                    || value.stripTrailingZeros().scale() > 0) throw invalid("price." + field);
            return value;
        } catch (NumberFormatException ex) { throw invalid("price." + field); }
    }

    private String orderCode(JsonNode node, String field) {
        String value = text(node, field, 50);
        if (!value.matches("[A-Za-z0-9_-]{1,50}")) throw invalid(field);
        return value;
    }

    private String webUrl(JsonNode node, String field) {
        String value = text(node, field, 4000);
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw invalid(field);
            return value;
        } catch (IllegalArgumentException ex) { throw invalid(field); }
    }

    private String text(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > maxLength)
            throw invalid(field);
        return value.asText();
    }

    private void rejectFuture(Instant time, String field) {
        if (time.isAfter(Instant.now().plusSeconds(properties.getWebhookMaxFutureSkewSeconds()))) throw invalid(field);
    }

    private static boolean constantTimeText(String expected, String supplied) {
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static BadRequestException invalid(String field) {
        return new BadRequestException("Invalid Lalamove webhook field: " + field);
    }
}
