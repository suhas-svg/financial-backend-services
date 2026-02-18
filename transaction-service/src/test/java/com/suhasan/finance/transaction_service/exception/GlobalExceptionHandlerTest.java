package com.suhasan.finance.transaction_service.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @BeforeEach
    void setUp() {
        // Setup if needed
    }

    @Test
    void handleIllegalArgumentException_ReturnsCorrectErrorResponse() {
        // Arrange
        String errorMessage = "Invalid account ID";
        IllegalArgumentException exception = new IllegalArgumentException(errorMessage);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleIllegalArgumentException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Bad Request", errorResponse.getError());
        assertEquals(errorMessage, errorResponse.getMessage());
        assertEquals("/api/transactions", errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void handleTransactionNotFoundException_ReturnsNotFoundResponse() {
        // Arrange
        String transactionId = "txn123";
        String errorMessage = "Transaction not found: " + transactionId;
        TransactionNotFoundException exception = new TransactionNotFoundException(errorMessage);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleTransactionNotFoundException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.NOT_FOUND.value(), errorResponse.getStatus());
        assertEquals("Not Found", errorResponse.getError());
        assertEquals(errorMessage, errorResponse.getMessage());
    }

    @Test
    void handleInsufficientFundsException_ReturnsBadRequestResponse() {
        // Arrange
        String errorMessage = "Insufficient funds for transaction";
        InsufficientFundsException exception = new InsufficientFundsException(errorMessage);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInsufficientFundsException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Insufficient Funds", errorResponse.getError());
        assertEquals(errorMessage, errorResponse.getMessage());
        assertEquals("/api/transactions", errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void handleTransactionLimitExceededException_ReturnsBadRequestResponse() {
        // Arrange
        String errorMessage = "Daily transaction limit exceeded";
        TransactionLimitExceededException exception = new TransactionLimitExceededException(errorMessage);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleTransactionLimitExceededException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Transaction Limit Exceeded", errorResponse.getError());
        assertEquals(errorMessage, errorResponse.getMessage());
        assertEquals("/api/transactions", errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void handleTransactionAlreadyReversedException_ReturnsConflictResponse() {
        // Arrange
        String transactionId = "txn123";
        String errorMessage = "Transaction already reversed";
        TransactionAlreadyReversedException exception = new TransactionAlreadyReversedException(transactionId, errorMessage);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleTransactionAlreadyReversedException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.CONFLICT.value(), errorResponse.getStatus());
        assertEquals("Transaction Already Reversed", errorResponse.getError());
        assertEquals(errorMessage, errorResponse.getMessage());
        assertEquals("/api/transactions", errorResponse.getPath());
        assertEquals(transactionId, errorResponse.getTransactionId());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void handleValidationExceptions_ReturnsValidationErrorResponse() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError1 = new FieldError("transferRequest", "amount", "Amount must be positive");
        FieldError fieldError2 = new FieldError("transferRequest", "fromAccountId", "From account ID is required");
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(Arrays.asList(fieldError1, fieldError2));

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.BAD_REQUEST.value(), errorResponse.getStatus());
        assertEquals("Validation Failed", errorResponse.getError());
        assertEquals("Invalid input parameters", errorResponse.getMessage());
        assertEquals("/api/transactions", errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
        
        Map<String, String> validationErrors = errorResponse.getValidationErrors();
        assertNotNull(validationErrors);
        assertEquals(2, validationErrors.size());
        assertEquals("Amount must be positive", validationErrors.get("amount"));
        assertEquals("From account ID is required", validationErrors.get("fromAccountId"));
    }

    @Test
    void handleRuntimeException_ReturnsInternalServerErrorResponse() {
        // Arrange
        String errorMessage = "Database connection failed";
        RuntimeException exception = new RuntimeException(errorMessage);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleRuntimeException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), errorResponse.getStatus());
        assertEquals("Internal Server Error", errorResponse.getError());
        assertEquals("An unexpected error occurred", errorResponse.getMessage());
        assertEquals("/api/transactions", errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void handleGenericException_ReturnsInternalServerErrorResponse() {
        // Arrange
        String errorMessage = "Unexpected system error";
        Exception exception = new Exception(errorMessage);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleGenericException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), errorResponse.getStatus());
        assertEquals("Internal Server Error", errorResponse.getError());
        assertEquals("An unexpected error occurred", errorResponse.getMessage());
        assertEquals("/api/transactions", errorResponse.getPath());
        assertNotNull(errorResponse.getTimestamp());
    }

    @Test
    void handleValidationExceptions_EmptyErrors_ReturnsValidationErrorResponse() {
        // Arrange
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(Arrays.asList());

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleValidationExceptions(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("Validation Failed", errorResponse.getError());
        assertEquals("Invalid input parameters", errorResponse.getMessage());
        
        Map<String, String> validationErrors = errorResponse.getValidationErrors();
        assertNotNull(validationErrors);
        assertTrue(validationErrors.isEmpty());
    }

    @Test
    void handleIllegalArgumentException_NullMessage_HandlesGracefully() {
        // Arrange
        IllegalArgumentException exception = new IllegalArgumentException((String) null);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleIllegalArgumentException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertNull(errorResponse.getMessage()); // Should handle null message gracefully
    }

    @Test
    void handleRuntimeException_NullMessage_HandlesGracefully() {
        // Arrange
        RuntimeException exception = new RuntimeException((String) null);

        // Act
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleRuntimeException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        
        ErrorResponse errorResponse = response.getBody();
        assertNotNull(errorResponse);
        assertEquals("An unexpected error occurred", errorResponse.getMessage()); // Should use default message
    }
}
