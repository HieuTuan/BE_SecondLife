package com.secondlife.secondlife.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    boolean success,
    String message,
    String path,
    List<FieldErrorItem> errors,
    LocalDateTime timestamp
) {
    public record FieldErrorItem(String field, String message) {}

    public static ErrorResponse of(String message, String path, List<FieldErrorItem> errors) {
        return new ErrorResponse(false, message, path, errors != null ? errors : List.of(), LocalDateTime.now());
    }

    public static ErrorResponse of(String message, String path) {
        return new ErrorResponse(false, message, path, List.of(), LocalDateTime.now());
    }
}
