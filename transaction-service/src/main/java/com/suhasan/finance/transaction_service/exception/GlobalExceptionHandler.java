package com.suhasan.finance.transaction_service.exception;

import com.suhasan.finance.transaction_service.outcome.service.ScenarioDivergedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.suhasan.finance.transaction_service.exception.ApiProblems.problem;
import static com.suhasan.finance.transaction_service.exception.ApiProblems.response;

/** Maps exceptions to RFC 9457 Problem Details; see {@link ApiProblems}. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ScenarioDivergedException.class)
    public ResponseEntity<ProblemDetail> handleScenarioDivergedException(ScenarioDivergedException ex,
                                                                        HttpServletRequest req) {
        log.warn("Outcome Protection source freshness rejected an operation");
        ProblemDetail body = problem(HttpStatus.CONFLICT, "scenario-diverged", "Scenario Diverged",
                ScenarioDivergedException.RECOVERY, req);
        // Clients branch on this stable code.
        body.setProperty("error", ScenarioDivergedException.CODE);
        return response(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                       HttpServletRequest req) {
        log.error("Illegal argument exception: {}", ex.getMessage());
        return response(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleTransactionNotFoundException(TransactionNotFoundException ex,
                                                                           HttpServletRequest req) {
        log.error("Transaction not found: {}", ex.getMessage());
        return response(HttpStatus.NOT_FOUND, "transaction-not-found", "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalStateException(IllegalStateException ex,
                                                                    HttpServletRequest req) {
        log.warn("Transaction state conflict: {}", ex.getMessage());
        return response(HttpStatus.CONFLICT, "conflict", "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientFundsException(InsufficientFundsException ex,
                                                                         HttpServletRequest req) {
        log.error("Insufficient funds: {}", ex.getMessage());
        return response(HttpStatus.BAD_REQUEST, "insufficient-funds", "Insufficient Funds", ex.getMessage(), req);
    }

    @ExceptionHandler(TransactionLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleTransactionLimitExceededException(TransactionLimitExceededException ex,
                                                                                HttpServletRequest req) {
        log.error("Transaction limit exceeded: {}", ex.getMessage());
        return response(HttpStatus.BAD_REQUEST, "transaction-limit-exceeded", "Transaction Limit Exceeded",
                ex.getMessage(), req);
    }

    @ExceptionHandler(TransactionAlreadyReversedException.class)
    public ResponseEntity<ProblemDetail> handleTransactionAlreadyReversedException(
            TransactionAlreadyReversedException ex, HttpServletRequest req) {
        log.error("Transaction already reversed: {}", ex.getMessage());
        ProblemDetail body = problem(HttpStatus.CONFLICT, "transaction-already-reversed",
                "Transaction Already Reversed", ex.getMessage(), req);
        body.setProperty("transactionId", ex.getTransactionId());
        return response(body);
    }

    @ExceptionHandler(AccountServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleAccountServiceUnavailableException(
            AccountServiceUnavailableException ex, HttpServletRequest req) {
        log.error("Account service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "account-service-unavailable", "Service Unavailable",
                        ex.getMessage(), req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex,
                                                                    HttpServletRequest req) {
        return response(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex,
                                                                   HttpServletRequest req) {
        log.error("Validation error: {}", ex.getMessage());
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Validation Failed",
                "Invalid input parameters", req);
        body.setProperty("validationErrors", errors);
        return response(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex,
                                                                              HttpServletRequest req) {
        log.error("Malformed request body: {}", ex.getMessage());
        return response(HttpStatus.BAD_REQUEST, "malformed-json", "Bad Request", "Invalid request body format", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        log.error("Unsupported media type: {}", ex.getMessage());
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-media-type", "Unsupported Media Type",
                "Content type must be application/json", req);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetail> handleMissingRequestHeaderException(MissingRequestHeaderException ex,
                                                                            HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request",
                "Required request header is missing: " + ex.getHeaderName(), req);
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ProblemDetail> handleBindingException(ServletRequestBindingException ex,
                                                               HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request", ex.getMessage(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                           HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "bad-request", "Bad Request",
                "Invalid value for parameter '" + ex.getName() + "'", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFoundException(NoResourceFoundException ex,
                                                                       HttpServletRequest req) {
        log.error("Resource not found: {}", ex.getMessage());
        return response(HttpStatus.NOT_FOUND, "not-found", "Not Found", "Resource not found", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                 HttpServletRequest req) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method Not Allowed",
                "HTTP method is not supported for this resource", req);
    }

    /** ResponseStatusException and other Spring exceptions that carry their own status. */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> handleErrorResponse(ErrorResponseException ex, HttpServletRequest req) {
        return fromErrorResponse(ex, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse withStatus && withStatus.getStatusCode().is4xxClientError()) {
            return fromErrorResponse(withStatus, req);
        }
        log.error("Unexpected exception: {}", ex.getMessage(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Internal Server Error",
                "An unexpected error occurred", req);
    }

    private static ResponseEntity<ProblemDetail> fromErrorResponse(ErrorResponse ex, HttpServletRequest req) {
        HttpStatus resolved = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus status = resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
        String detail = ex.getBody().getDetail() == null ? status.getReasonPhrase() : ex.getBody().getDetail();
        String slug = status.getReasonPhrase().toLowerCase(Locale.ROOT).replace(' ', '-');
        return response(status, slug, status.getReasonPhrase(), detail, req);
    }
}
