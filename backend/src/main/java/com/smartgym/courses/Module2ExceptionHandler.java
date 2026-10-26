package com.smartgym.courses;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class Module2ExceptionHandler {
    @ExceptionHandler(Module2Exception.class)
    public ResponseEntity<Map<String, String>> handleModule2Exception(Module2Exception exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("detail", exception.getReason() == null ? "The request could not be completed." : exception.getReason()));
    }
}
