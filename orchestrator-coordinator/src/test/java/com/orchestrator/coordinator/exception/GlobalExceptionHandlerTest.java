package com.orchestrator.coordinator.exception;

import com.orchestrator.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/api/v1/schedules/invalid-id");
    }

    @Test
    @DisplayName("Should map IllegalArgumentException to 400 BAD_REQUEST ErrorResponse")
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Schedule not found with ID: invalid-id");

        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleIllegalArgumentException(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(400, responseEntity.getBody().getStatus());
        assertEquals("Schedule not found with ID: invalid-id", responseEntity.getBody().getMessage());
        assertEquals("/api/v1/schedules/invalid-id", responseEntity.getBody().getPath());
    }

    @Test
    @DisplayName("Should map IllegalStateException to 409 CONFLICT ErrorResponse")
    void shouldHandleIllegalStateException() {
        IllegalStateException ex = new IllegalStateException("Associated schedule not active");

        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleIllegalStateException(ex, request);

        assertEquals(HttpStatus.CONFLICT, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(409, responseEntity.getBody().getStatus());
        assertEquals("Associated schedule not active", responseEntity.getBody().getMessage());
    }

    @Test
    @DisplayName("Should handle generic unexpected exceptions cleanly without exposing stack traces")
    void shouldHandleGenericException() {
        RuntimeException ex = new RuntimeException("Unexpected NullPointerException in deep internal call");

        ResponseEntity<ErrorResponse> responseEntity = exceptionHandler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(500, responseEntity.getBody().getStatus());
        assertEquals("An unexpected error occurred. Please contact system administrator.", responseEntity.getBody().getMessage());
    }
}
