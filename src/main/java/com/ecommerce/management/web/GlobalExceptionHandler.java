package com.ecommerce.management.web;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.PropertyNamingStrategies;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            String field = PropertyNamingStrategies.SNAKE_CASE.nameForField(null, null, error.getField());
            @SuppressWarnings("unchecked")
            var messages = (ArrayList<String>) fields.computeIfAbsent(field, key -> new ArrayList<String>());
            messages.add(error.getDefaultMessage());
        });
        return response(HttpStatus.valueOf(422), headers, "VALIDATION_ERROR",
                "Request validation failed", fields, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = HttpStatus.valueOf(status.value()).getReasonPhrase();
        if (ex instanceof ResponseStatusException responseStatus && responseStatus.getReason() != null
                && !status.is5xxServerError()) {
            message = responseStatus.getReason();
        }
        if (status.is5xxServerError()) {
            LOG.error("Request failed", ex);
            message = "An unexpected error occurred";
        }
        return response(status, headers, code(status), message, Map.of(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleConflict(DataIntegrityViolationException ex, WebRequest request) {
        return response(HttpStatus.CONFLICT, new HttpHeaders(), "CONFLICT",
                "The operation conflicts with existing data", Map.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        LOG.error("Unexpected request failure", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(), "INTERNAL_ERROR",
                "An unexpected error occurred", Map.of(), request);
    }

    private String code(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "BAD_REQUEST";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 422 -> "VALIDATION_ERROR";
            case 429 -> "RATE_LIMIT_EXCEEDED";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "HTTP_" + status.value();
        };
    }

    private ResponseEntity<Object> response(HttpStatusCode status, HttpHeaders headers, String code,
            String message, Map<String, Object> details, WebRequest request) {
        String correlationId = (String) request.getAttribute(CorrelationIdFilter.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return new ResponseEntity<>(new ApiErrorResponse(
                new ApiErrorResponse.ApiError(code, message, details, correlationId)), headers, status);
    }
}
