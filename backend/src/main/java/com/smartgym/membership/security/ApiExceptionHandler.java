package com.smartgym.membership.security;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return response(status, status.name(), messageFor(status), Map.of());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class,
        HandlerMethodValidationException.class
    })
    ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception) {
        Map<String, Object> details = exception instanceof MethodArgumentNotValidException validationException
                ? Map.of("fieldErrors", validationException.getBindingResult().getFieldErrors().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                org.springframework.validation.FieldError::getField,
                                error -> error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage(),
                                (first, ignored) -> first)))
                : Map.of();
        return response(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request is invalid.", details);
    }

    @ExceptionHandler({
        NoHandlerFoundException.class,
        NoResourceFoundException.class
    })
    ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception) {
        return response(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                "The requested resource was not found.",
                Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(DataIntegrityViolationException exception) {
        return response(
                HttpStatus.CONFLICT,
                "CONFLICT",
                "The request conflicts with existing data.",
                Map.of());
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, details));
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request is invalid.";
            case UNAUTHORIZED -> "Authentication is required or the access token is invalid.";
            case FORBIDDEN -> "You are not allowed to access this resource.";
            case NOT_FOUND -> "The requested resource was not found.";
            case CONFLICT -> "The request conflicts with existing data.";
            default -> "The request could not be completed.";
        };
    }
}
