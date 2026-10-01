package com.suhasan.finance.transaction_service.exception;

import com.suhasan.finance.transaction_service.outcome.service.ScenarioDivergedException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    private static MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("POST", uri);
    }

    private static Object prop(ProblemDetail body, String name) {
        assertNotNull(body.getProperties(), "extension members");
        return body.getProperties().get(name);
    }

    /** Every error is a Problem Detail with the legacy fields kept for existing clients. */
    private static ProblemDetail assertProblem(ResponseEntity<ProblemDetail> response, HttpStatus status,
                                               String slug, String title, String path) {
        assertEquals(status, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, response.getHeaders().getContentType());
        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals(status.value(), body.getStatus());
        assertEquals(URI.create("urn:financial:problem:" + slug), body.getType());
        assertEquals(title, body.getTitle());
        assertEquals(URI.create(path), body.getInstance());
        assertEquals(path, prop(body, "path"));
        assertEquals(body.getDetail(), prop(body, "message"));
        assertNotNull(prop(body, "timestamp"));
        return body;
    }

    @Test
    void handleScenarioDivergedReturnsStableCustomerSafeConflict() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleScenarioDivergedException(
                new ScenarioDivergedException(), request("/api/outcome-protection/scenarios/7/actions"));

        ProblemDetail body = assertProblem(response, HttpStatus.CONFLICT, "scenario-diverged", "Scenario Diverged",
                "/api/outcome-protection/scenarios/7/actions");
        assertEquals("SCENARIO_DIVERGED", prop(body, "error"));
        assertEquals(ScenarioDivergedException.RECOVERY, body.getDetail());
    }

    @Test
    void handleIllegalArgumentException_ReportsTheRealRequestPath() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleIllegalArgumentException(
                new IllegalArgumentException("Invalid account ID"), request("/api/scheduled-transfers"));

        ProblemDetail body = assertProblem(response, HttpStatus.BAD_REQUEST, "bad-request", "Bad Request",
                "/api/scheduled-transfers");
        assertEquals("Bad Request", prop(body, "error"));
        assertEquals("Invalid account ID", body.getDetail());
    }

    @Test
    void handleTransactionNotFoundException_ReturnsNotFound() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleTransactionNotFoundException(
                new TransactionNotFoundException("Transaction not found: txn123"), request("/api/transactions/txn123"));

        ProblemDetail body = assertProblem(response, HttpStatus.NOT_FOUND, "transaction-not-found", "Not Found",
                "/api/transactions/txn123");
        assertEquals("Transaction not found: txn123", body.getDetail());
    }

    @Test
    void handleInsufficientFundsException_ReturnsBadRequest() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleInsufficientFundsException(
                new InsufficientFundsException("Insufficient funds for transaction"),
                request("/api/transactions/transfer"));

        ProblemDetail body = assertProblem(response, HttpStatus.BAD_REQUEST, "insufficient-funds",
                "Insufficient Funds", "/api/transactions/transfer");
        assertEquals("Insufficient Funds", prop(body, "error"));
    }

    @Test
    void handleTransactionLimitExceededException_ReturnsBadRequest() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleTransactionLimitExceededException(
                new TransactionLimitExceededException("Daily transaction limit exceeded"),
                request("/api/transactions/withdraw"));

        assertProblem(response, HttpStatus.BAD_REQUEST, "transaction-limit-exceeded", "Transaction Limit Exceeded",
                "/api/transactions/withdraw");
    }

    @Test
    void handleTransactionAlreadyReversedException_ReturnsConflictWithTransactionId() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleTransactionAlreadyReversedException(
                new TransactionAlreadyReversedException("txn123", "Transaction already reversed"),
                request("/api/transactions/txn123/reverse"));

        ProblemDetail body = assertProblem(response, HttpStatus.CONFLICT, "transaction-already-reversed",
                "Transaction Already Reversed", "/api/transactions/txn123/reverse");
        assertEquals("txn123", prop(body, "transactionId"));
    }

    @Test
    void handleValidationExceptions_ListsEveryInvalidField() {
        MethodArgumentNotValidException exception = validationException(
                new FieldError("transferRequest", "amount", "Amount must be positive"),
                new FieldError("transferRequest", "fromAccountId", "From account ID is required"));

        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleValidationExceptions(
                exception, request("/api/transactions/transfer"));

        ProblemDetail body = assertProblem(response, HttpStatus.BAD_REQUEST, "validation-failed", "Validation Failed",
                "/api/transactions/transfer");
        assertEquals("Invalid input parameters", body.getDetail());
        @SuppressWarnings("unchecked")
        Map<String, String> validationErrors = (Map<String, String>) prop(body, "validationErrors");
        assertEquals(Map.of("amount", "Amount must be positive", "fromAccountId", "From account ID is required"),
                validationErrors);
    }

    @Test
    void handleValidationExceptions_EmptyErrors() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleValidationExceptions(
                validationException(), request("/api/transactions/transfer"));

        ProblemDetail body = response.getBody();
        assertNotNull(body);
        assertEquals(Map.of(), prop(body, "validationErrors"));
    }

    @Test
    void handleGenericException_HidesInternalDetail() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleGenericException(
                new RuntimeException("Database connection failed"), request("/api/transactions"));

        ProblemDetail body = assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal Server Error", "/api/transactions");
        assertEquals("An unexpected error occurred", body.getDetail());
    }

    @Test
    void handleGenericException_NullMessage() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleGenericException(
                new RuntimeException((String) null), request("/api/transactions"));

        assertNotNull(response.getBody());
        assertEquals("An unexpected error occurred", response.getBody().getDetail());
    }

    @Test
    void handleIllegalArgumentException_NullMessage() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleIllegalArgumentException(
                new IllegalArgumentException((String) null), request("/api/transactions"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNull(response.getBody().getDetail());
    }

    @Test
    void responseStatusException_KeepsItsStatusInsteadOfBecoming500() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleErrorResponse(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"),
                request("/api/scheduled-transfers/abc"));

        ProblemDetail body = assertProblem(response, HttpStatus.NOT_FOUND, "not-found", "Not Found",
                "/api/scheduled-transfers/abc");
        assertEquals("Schedule not found", body.getDetail());
    }

    @Test
    void moneyMovementBusy_IsAFastRetryable503() {
        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleMoneyMovementBusyException(
                new MoneyMovementBusyException(), request("/api/transactions/transfer"));

        ProblemDetail body = assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE, "money-movement-busy",
                "Service Busy", "/api/transactions/transfer");
        assertEquals("1", response.getHeaders().getFirst("Retry-After"));
        assertTrue(body.getDetail().contains("Nothing was charged"));
    }

    @Test
    void typeMismatch_IsABadRequestNotAServerError() {
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, new NumberFormatException("abc"));

        ResponseEntity<ProblemDetail> response = globalExceptionHandler.handleTypeMismatch(
                exception, request("/api/ledger/accounts/abc"));

        ProblemDetail body = assertProblem(response, HttpStatus.BAD_REQUEST, "bad-request", "Bad Request",
                "/api/ledger/accounts/abc");
        assertEquals("Invalid value for parameter 'id'", body.getDetail());
    }

    private MethodArgumentNotValidException validationException(FieldError... fieldErrors) {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "transferRequest");
        Arrays.stream(fieldErrors).forEach(bindingResult::addError);

        try {
            Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("validationTarget", Object.class);
            return new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
        } catch (NoSuchMethodException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @SuppressWarnings("unused")
    private void validationTarget(Object request) {
    }
}
