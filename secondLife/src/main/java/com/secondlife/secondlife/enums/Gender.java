package com.secondlife.secondlife.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

public enum Gender {
    MALE, FEMALE, OTHER;

    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static Gender fromJson(JsonNode value) {
        if(value==null || value.isNull()) return null;
        if(!value.isString()) throw new IllegalArgumentException("Gender must be MALE, FEMALE or OTHER");
        return Gender.valueOf(value.asText());
    }
}
