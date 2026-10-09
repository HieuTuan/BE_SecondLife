package com.secondlife.secondlife.exception;

/** A request field error whose safe message can be returned without exposing its value. */
public class InvalidRequestFieldException extends BadRequestException {
    private final String field;

    public InvalidRequestFieldException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
