package com.secondlife.secondlife.dto.commission;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;

/** Strict JSON types for commission requests without changing other API contracts. */
public final class CommissionJsonTypes {
    private CommissionJsonTypes() {}

    public static final class NumberValue extends ValueDeserializer<BigDecimal> {
        @Override public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT && parser.currentToken() != JsonToken.VALUE_NUMBER_FLOAT)
                return context.reportInputMismatch(BigDecimal.class, "Must be a JSON number");
            return parser.getDecimalValue();
        }
    }

    public static final class BooleanValue extends ValueDeserializer<Boolean> {
        @Override public Boolean deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_TRUE && parser.currentToken() != JsonToken.VALUE_FALSE)
                return context.reportInputMismatch(Boolean.class, "Must be a JSON boolean");
            return parser.getBooleanValue();
        }
    }

    public static final class StringValue extends ValueDeserializer<String> {
        @Override public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_STRING)
                return context.reportInputMismatch(String.class, "Must be a JSON string");
            return parser.getString();
        }
    }
}
