package com.smartgym.membership.security;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsConflictToUniformJsonWithoutDatabaseDetails() {
        var response = handler.handleConflict(new DataIntegrityViolationException("private database detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "CONFLICT",
                "The request conflicts with existing data.",
                Map.of()));
    }

    @Test
    void mapsNotFoundToUniformJson() {
        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "sensitive internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                "NOT_FOUND",
                "The requested resource was not found.",
                Map.of()));
    }
}
