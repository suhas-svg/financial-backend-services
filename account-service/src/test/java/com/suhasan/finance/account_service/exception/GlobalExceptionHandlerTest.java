package com.suhasan.finance.account_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static HttpServletRequest request(final String uri) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void handleNoResourceFound_Returns404ProblemDetail() {
        final NoResourceFoundException exception = mock(NoResourceFoundException.class);
        when(exception.getMessage()).thenReturn("No static resource api/nonexistent-endpoint-xyz.");

        final ResponseEntity<ProblemDetail> response =
                handler.handleNoResourceFound(exception, request("/api/nonexistent-endpoint-xyz"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        final ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("urn:financial:problem:not-found"));
        assertThat(body.getTitle()).isEqualTo("Not Found");
        assertThat(body.getDetail()).isEqualTo("No static resource api/nonexistent-endpoint-xyz.");
        assertThat(body.getInstance()).isEqualTo(URI.create("/api/nonexistent-endpoint-xyz"));
        assertThat(body.getStatus()).isEqualTo(404);
        // Pre-RFC fields stay for existing clients.
        assertThat(body.getProperties())
                .containsEntry("error", "Not Found")
                .containsEntry("message", "No static resource api/nonexistent-endpoint-xyz.")
                .containsEntry("path", "/api/nonexistent-endpoint-xyz")
                .containsKey("timestamp");
    }

    @Test
    void handleMethodNotSupported_Returns405ProblemDetail() {
        final ResponseEntity<ProblemDetail> response = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"), request("/api/accounts/42"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Method Not Allowed");
        assertThat(response.getBody().getStatus()).isEqualTo(405);
    }

    @Test
    void handleAuthenticationException_Returns401ProblemDetail() {
        final AuthenticationException exception = mock(AuthenticationException.class);
        when(exception.getMessage()).thenReturn("Bad credentials");

        final ResponseEntity<ProblemDetail> response =
                handler.handleAuthenticationException(exception, request("/api/auth/login"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Unauthorized");
        assertThat(response.getBody().getDetail()).isEqualTo("Bad credentials");
        assertThat(response.getBody().getProperties()).containsEntry("path", "/api/auth/login");
    }

    @Test
    void handleTypeMismatch_Returns400InsteadOfServerError() {
        final MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "phase1-inr", Long.class, "id", null, new NumberFormatException("For input string: \"phase1-inr\""));

        final ResponseEntity<ProblemDetail> response =
                handler.handleTypeMismatch(exception, request("/api/internal/accounts/phase1-inr"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Bad Request");
        assertThat(response.getBody().getDetail()).isEqualTo("Invalid value for parameter 'id'");
    }

    @Test
    void handleMfaVerification_Returns400ProblemDetail() {
        final ResponseEntity<ProblemDetail> response = handler.handleMfaVerification(
                new MfaVerificationException("Invalid verification credential"),
                request("/api/security/challenges/challenge-1/verify"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Verification Failed");
    }

    @Test
    void handleTooManyAttempts_Returns429WithRetryAfter() {
        final ResponseEntity<ProblemDetail> response =
                handler.handleTooManyAttempts(new TooManyAttemptsException(120), request("/api/auth/login"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("120");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Too many attempts. Try again later.");
    }

    @Test
    void unexpectedErrors_DoNotLeakInternalDetail() {
        final ResponseEntity<ProblemDetail> response = handler.handleAll(
                new RuntimeException("jdbc:postgresql://db/accounts password=hunter2"), request("/api/accounts"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().getProperties().toString()).doesNotContain("hunter2");
    }

    @Test
    void responseStatusException_KeepsItsStatus() {
        final ResponseEntity<ProblemDetail> response = handler.handleErrorResponse(
                new ResponseStatusException(HttpStatus.GONE, "Challenge expired"), request("/api/security/challenges/1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail()).isEqualTo("Challenge expired");
        assertThat(response.getBody().getType()).isEqualTo(URI.create("urn:financial:problem:gone"));
    }
}
