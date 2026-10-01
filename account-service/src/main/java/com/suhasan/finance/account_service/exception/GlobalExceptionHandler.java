package com.suhasan.finance.account_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.suhasan.finance.account_service.exception.ApiProblems.problem;
import static com.suhasan.finance.account_service.exception.ApiProblems.response;

/** Maps exceptions to RFC 9457 Problem Details; see {@link ApiProblems}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleJsonParse(final HttpMessageNotReadableException ex,
                                                         final HttpServletRequest req) {
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getMessage();
        }
        return response(HttpStatus.BAD_REQUEST, "malformed-json", "Malformed JSON", msg, req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(final MethodArgumentNotValidException ex,
                                                          final HttpServletRequest req) {
        final FieldError first = ex.getBindingResult().getFieldErrors().get(0);
        final Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        final ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Validation Failed",
                first.getField() + ": " + first.getDefaultMessage(), req);
        body.setProperty("validationErrors", fields);
        return response(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(final MethodArgumentTypeMismatchException ex,
                                                            final HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request",
                "Invalid value for parameter '" + ex.getName() + "'", req);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, ServletRequestBindingException.class})
    public ResponseEntity<ProblemDetail> handleBinding(final ServletRequestBindingException ex,
                                                       final HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(final IllegalArgumentException ex,
                                                        final HttpServletRequest req) {
        return response(HttpStatus.NOT_FOUND, "not-found", "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(final HttpRequestMethodNotSupportedException ex,
                                                                  final HttpServletRequest req) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method Not Allowed",
                "HTTP method is not supported for this resource", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaType(final HttpMediaTypeNotSupportedException ex,
                                                         final HttpServletRequest req) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported Media Type",
                "Content type must be application/json", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(final NoResourceFoundException ex,
                                                               final HttpServletRequest req) {
        return response(HttpStatus.NOT_FOUND, "not-found", "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(MfaVerificationException.class)
    public ResponseEntity<ProblemDetail> handleMfaVerification(final MfaVerificationException ex,
                                                               final HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "verification-failed", "Verification Failed", ex.getMessage(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(final AccessDeniedException ex,
                                                            final HttpServletRequest req) {
        return response(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleConflict(final IllegalStateException ex,
                                                        final HttpServletRequest req) {
        return response(HttpStatus.CONFLICT, "conflict", "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(final AuthenticationException ex,
                                                                       final HttpServletRequest req) {
        return response(HttpStatus.UNAUTHORIZED, "unauthorized", "Unauthorized", ex.getMessage(), req);
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ProblemDetail> handleTooManyAttempts(final TooManyAttemptsException ex,
                                                               final HttpServletRequest req) {
        final ProblemDetail body = problem(HttpStatus.TOO_MANY_REQUESTS, "too-many-requests", "Too Many Requests",
                ex.getMessage(), req);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    /** ResponseStatusException and other Spring exceptions that already carry a status. */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> handleErrorResponse(final ErrorResponseException ex,
                                                             final HttpServletRequest req) {
        final HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        final HttpStatus resolved = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        final String detail = ex.getBody().getDetail() == null ? resolved.getReasonPhrase() : ex.getBody().getDetail();
        return response(resolved, slug(resolved), resolved.getReasonPhrase(), detail, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAll(final Exception ex, final HttpServletRequest req) {
        if (ex instanceof ErrorResponse withStatus) {
            final HttpStatus status = HttpStatus.resolve(withStatus.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return response(status, slug(status), status.getReasonPhrase(),
                        withStatus.getBody().getDetail(), req);
            }
        }
        // Never echo internal exception text to the caller; it is in the log.
        LOG.error("Unhandled exception on {}", req.getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred", req);
    }

    private static String slug(final HttpStatus status) {
        return status.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}
