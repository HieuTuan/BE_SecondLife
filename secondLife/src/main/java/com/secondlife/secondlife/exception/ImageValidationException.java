package com.secondlife.secondlife.exception;

import com.secondlife.secondlife.common.ErrorResponse;

import org.springframework.http.HttpStatus;

import java.util.List;

/** Safe per-file errors; keeps the existing API error envelope. */
public class ImageValidationException extends InvalidRequestFieldException {
    private final HttpStatus status;
    private final List<ErrorResponse.FieldErrorItem> errors;

    public ImageValidationException(HttpStatus status, List<ErrorResponse.FieldErrorItem> errors) {
        super(errors.getFirst().field(), errors.getFirst().message());
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public HttpStatus getStatus() { return status; }
    public List<ErrorResponse.FieldErrorItem> getErrors() { return errors; }
}
