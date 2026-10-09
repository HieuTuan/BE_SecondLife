package com.secondlife.secondlife.exception;

import com.secondlife.secondlife.common.ErrorResponse;
import com.secondlife.secondlife.service.ekyc.vnpt.VnptApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleEmailDelivery(EmailDeliveryException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler(ShippingProviderException.class)
    public ResponseEntity<ErrorResponse> handleShippingProvider(ShippingProviderException ex,HttpServletRequest request) {
        HttpStatus status = ex.getUpstreamStatus()==503 ? HttpStatus.SERVICE_UNAVAILABLE :
                ex.getUpstreamStatus()==504 ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(ErrorResponse.of(ex.getMessage(),request.getRequestURI()));
    }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleMissingEndpoint(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("Endpoint not found", request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMedia(org.springframework.web.HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of("Unsupported Content-Type for this endpoint", request.getRequestURI()));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException mismatch) {
            return ResponseEntity.badRequest().body(ErrorResponse.of("Invalid parameter format", request.getRequestURI(),
                    List.of(new ErrorResponse.FieldErrorItem(mismatch.getName(), "Invalid parameter format"))));
        }
        for (Throwable cause = ex.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidRequestFieldException invalidField) {
                return ResponseEntity.badRequest().body(ErrorResponse.of("Validation failed", request.getRequestURI(),
                        List.of(new ErrorResponse.FieldErrorItem(invalidField.getField(), invalidField.getMessage()))));
            }
            if (cause instanceof tools.jackson.core.JacksonException jsonError && !jsonError.getPath().isEmpty()) {
                String field = jsonError.getPath().stream().map(tools.jackson.core.JacksonException.Reference::getPropertyName)
                        .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining("."));
                if (!field.isEmpty()) {
                    return ResponseEntity.badRequest().body(ErrorResponse.of("Invalid JSON field", request.getRequestURI(),
                            List.of(new ErrorResponse.FieldErrorItem(field, "Invalid value, JSON type, or unsupported field"))));
                }
            }
        }
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("Malformed request body or parameter", request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.data.core.PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handlePropertyReferenceException(
            org.springframework.data.core.PropertyReferenceException ex, HttpServletRequest request) {
        log.warn("Invalid sort property at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("Invalid sort parameter: " + ex.getPropertyName(), request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument at {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(AiProviderException.class)
    public ResponseEntity<ErrorResponse> handleAiProvider(AiProviderException ex, HttpServletRequest request) {
        log.warn("AI provider failed at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(VnptApiException.class)
    public ResponseEntity<ErrorResponse> handleVnptApi(VnptApiException ex, HttpServletRequest request) {
        log.warn("VNPT request failed at {}: endpoint={}, status={}, code={}",
                request.getRequestURI(), ex.getEndpoint(), ex.getUpstreamStatus(), ex.getProviderCode());
        HttpStatus status = ex.getUpstreamStatus() == 429 || ex.getUpstreamStatus() >= 500
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(ErrorResponse.of("VNPT eKYC service could not complete verification", request.getRequestURI()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        log.warn("Bad request at {}", request.getRequestURI());
        if (ex instanceof InvalidRequestFieldException invalidField) {
            return ResponseEntity.badRequest().body(ErrorResponse.of("Validation failed", request.getRequestURI(),
                    List.of(new ErrorResponse.FieldErrorItem(invalidField.getField(), invalidField.getMessage()))));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized access at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        log.warn("Forbidden access at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldErrorItem> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ErrorResponse.FieldErrorItem(fieldError.getField(), fieldError.getDefaultMessage()));
        }
        log.warn("Validation failed at {}: {} field errors", request.getRequestURI(), errors.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("Validation failed", request.getRequestURI(), errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Bad credentials attempt at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("Invalid email or password", request.getRequestURI()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex, HttpServletRequest request) {
        log.warn("Disabled user attempted login at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("Account is disabled. Please contact support.", request.getRequestURI()));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(LockedException ex, HttpServletRequest request) {
        log.warn("Locked user attempted login at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("Account is locked. Please contact support.", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied for request {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("Access denied: You do not have permission to perform this action", request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request) {
        log.warn("Authentication failed at {}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("Authentication failed", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled {} processing request at {}", ex.getClass().getSimpleName(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("An unexpected internal error occurred. Please try again later.", request.getRequestURI()));
    }
}
