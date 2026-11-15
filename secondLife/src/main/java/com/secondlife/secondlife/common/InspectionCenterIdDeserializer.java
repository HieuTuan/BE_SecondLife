package com.secondlife.secondlife.common;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.util.UUID;

public final class InspectionCenterIdDeserializer extends ValueDeserializer<UUID> {
    @Override
    public UUID deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(UUID.class, "inspectionCenterId must be a UUID or AUTO");
        }
        String value = parser.getString().strip();
        if (value.isEmpty() || "AUTO".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            if (value.length() == 36) {
                UUID id = UUID.fromString(value);
                if (id.toString().equalsIgnoreCase(value)) {
                    return id;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Use the same field-specific error for all invalid UUID strings.
        }
        return context.reportInputMismatch(UUID.class, "inspectionCenterId must be a UUID or AUTO");
    }
}
