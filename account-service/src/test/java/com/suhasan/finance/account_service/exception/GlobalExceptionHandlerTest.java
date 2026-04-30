package com.suhasan.finance.account_service.exception;

import com.suhasan.finance.account_service.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNoResourceFound_Returns404Response() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        NoResourceFoundException exception = mock(NoResourceFoundException.class);

        when(request.getRequestURI()).thenReturn("/api/nonexistent-endpoint-xyz");
        when(exception.getMessage()).thenReturn("No static resource api/nonexistent-endpoint-xyz.");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Not Found");
        assertThat(response.getBody().getMessage()).isEqualTo("No static resource api/nonexistent-endpoint-xyz.");
        assertThat(response.getBody().getPath()).isEqualTo("/api/nonexistent-endpoint-xyz");
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }

    @Test
    void handleAuthenticationException_Returns401Response() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        AuthenticationException exception = mock(AuthenticationException.class);

        when(request.getRequestURI()).thenReturn("/api/auth/login");
        when(exception.getMessage()).thenReturn("Bad credentials");

        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError()).isEqualTo("Unauthorized");
        assertThat(response.getBody().getMessage()).isEqualTo("Bad credentials");
        assertThat(response.getBody().getPath()).isEqualTo("/api/auth/login");
        assertThat(response.getBody().getStatus()).isEqualTo(401);
    }
}
