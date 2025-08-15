package com.suhasan.finance.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.*;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.exception.GlobalExceptionHandler;
import com.suhasan.finance.transaction_service.exception.InsufficientFundsException;
import com.suhasan.finance.transaction_service.exception.TransactionAlreadyReversedException;
import com.suhasan.finance.transaction_service.exception.TransactionLimitExceededException;
import com.suhasan.finance.transaction_service.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import(GlobalExceptionHandler.class)
class TransactionControllerMockMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    private TransactionResponse transactionResponse;
    private TransferRequest transferRequest;
    private DepositRequest depositRequest;
    private WithdrawalRequest withdrawalRequest;

    @BeforeEach
    void setUp() {
        transactionResponse = TransactionResponse.builder()
                .transactionId("txn123")
                .fromAccountId("acc1")
                .toAccountId("acc2")
                .amount(BigDecimal.valueOf(100))
                .currency("USD")
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.COMPLETED)
                .description("Test transfer")
                .createdAt(LocalDateTime.now())
                .createdBy("user123")
                .build();

        transferRequest = TransferRequest.builder()
                .fromAccountId("acc1")
                .toAccountId("acc2")
                .amount(BigDecimal.valueOf(100))
                .currency("USD")
                .description("Test transfer")
                .reference("REF123")
                .build();

        depositRequest = DepositRequest.builder()
                .accountId("acc1")
                .amount(BigDecimal.valueOf(200))
                .description("Test deposit")
                .build();

        withdrawalRequest = WithdrawalRequest.builder()
                .accountId("acc1")
                .amount(BigDecimal.valueOf(150))
                .description("Test withdrawal")
                .build();
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_InsufficientFunds_ReturnsBadRequest() throws Exception {
        // Arrange
        when(transactionService.processTransfer(any(TransferRequest.class), eq("user123")))
                .thenThrow(new InsufficientFundsException("Insufficient funds for transaction"));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient Funds"))
                .andExpect(jsonPath("$.message").value("Insufficient funds for transaction"))
                .andExpect(jsonPath("$.status").value(400));

        verify(transactionService).processTransfer(any(TransferRequest.class), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_TransactionLimitExceeded_ReturnsBadRequest() throws Exception {
        // Arrange
        when(transactionService.processTransfer(any(TransferRequest.class), eq("user123")))
                .thenThrow(new TransactionLimitExceededException("Daily transaction limit exceeded"));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Transaction Limit Exceeded"))
                .andExpect(jsonPath("$.message").value("Daily transaction limit exceeded"))
                .andExpect(jsonPath("$.status").value(400));

        verify(transactionService).processTransfer(any(TransferRequest.class), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_ValidationErrors_ReturnsBadRequest() throws Exception {
        // Arrange
        TransferRequest invalidRequest = TransferRequest.builder()
                .fromAccountId("") // Invalid empty account ID
                .toAccountId("acc2")
                .amount(BigDecimal.valueOf(-100)) // Invalid negative amount
                .currency("USD")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("Invalid input parameters"))
                .andExpect(jsonPath("$.validationErrors").exists());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void processDeposit_ValidationErrors_ReturnsBadRequest() throws Exception {
        // Arrange
        DepositRequest invalidRequest = DepositRequest.builder()
                .accountId(null) // Invalid null account ID
                .amount(BigDecimal.ZERO) // Invalid zero amount
                .description("Test deposit")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/transactions/deposit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void processWithdrawal_InsufficientFunds_ReturnsBadRequest() throws Exception {
        // Arrange
        when(transactionService.processWithdrawal(eq("acc1"), eq(BigDecimal.valueOf(150)), 
                eq("Test withdrawal"), eq("user123")))
                .thenThrow(new InsufficientFundsException("Insufficient funds for withdrawal"));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/withdraw")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withdrawalRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient Funds"))
                .andExpect(jsonPath("$.message").value("Insufficient funds for withdrawal"));

        verify(transactionService).processWithdrawal(eq("acc1"), eq(BigDecimal.valueOf(150)), 
                eq("Test withdrawal"), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void reverseTransaction_AlreadyReversed_ReturnsConflict() throws Exception {
        // Arrange
        String transactionId = "txn123";
        ReversalRequest reversalRequest = ReversalRequest.builder()
                .reason("Customer request")
                .build();
        
        when(transactionService.reverseTransaction(eq(transactionId), eq("Customer request"), eq("user123")))
                .thenThrow(new TransactionAlreadyReversedException(transactionId, "Transaction already reversed"));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/{transactionId}/reverse", transactionId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reversalRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Transaction Already Reversed"))
                .andExpect(jsonPath("$.message").value("Transaction already reversed"))
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.status").value(409));

        verify(transactionService).reverseTransaction(eq(transactionId), eq("Customer request"), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void searchTransactions_WithFilters_ReturnsFilteredResults() throws Exception {
        // Arrange
        List<TransactionResponse> transactions = Arrays.asList(transactionResponse);
        Page<TransactionResponse> page = new PageImpl<>(transactions, PageRequest.of(0, 20), 1);
        
        when(transactionService.searchTransactions(any(TransactionFilterRequest.class), any()))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/search")
                .param("accountId", "acc1")
                .param("type", "TRANSFER")
                .param("status", "COMPLETED")
                .param("minAmount", "50")
                .param("maxAmount", "500")
                .param("description", "test")
                .param("startDate", "2024-01-01T00:00:00")
                .param("endDate", "2024-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn123"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(transactionService).searchTransactions(any(TransactionFilterRequest.class), any());
    }

    @Test
    @WithMockUser(username = "user123")
    void getAccountTransactionStats_Success() throws Exception {
        // Arrange
        String accountId = "acc1";
        TransactionStatsResponse statsResponse = TransactionStatsResponse.builder()
                .accountId(accountId)
                .totalTransactions(10L)
                .completedTransactions(8L)
                .totalAmount(BigDecimal.valueOf(1000))
                .successRate(80.0)
                .build();
        
        when(transactionService.getAccountTransactionStats(eq(accountId), any(), any()))
                .thenReturn(statsResponse);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/account/{accountId}/stats", accountId)
                .param("startDate", "2024-01-01T00:00:00")
                .param("endDate", "2024-12-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.totalTransactions").value(10))
                .andExpect(jsonPath("$.completedTransactions").value(8))
                .andExpect(jsonPath("$.totalAmount").value(1000))
                .andExpect(jsonPath("$.successRate").value(80.0));

        verify(transactionService).getAccountTransactionStats(eq(accountId), any(), any());
    }



    @Test
    @WithMockUser(username = "user123")
    void getAccountTransactions_WithPagination_ReturnsPagedResults() throws Exception {
        // Arrange
        String accountId = "acc1";
        List<TransactionResponse> transactions = Arrays.asList(transactionResponse);
        Page<TransactionResponse> page = new PageImpl<>(transactions, PageRequest.of(1, 5), 10);
        
        when(transactionService.getAccountTransactions(eq(accountId), any()))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/account/{accountId}", accountId)
                .param("page", "1")
                .param("size", "5")
                .param("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn123"))
                .andExpect(jsonPath("$.totalElements").value(10))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(5));

        verify(transactionService).getAccountTransactions(eq(accountId), any());
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_MalformedJson_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_MissingContentType_ReturnsUnsupportedMediaType() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void getTransaction_ServiceThrowsRuntimeException_ReturnsInternalServerError() throws Exception {
        // Arrange
        String transactionId = "txn123";
        when(transactionService.getTransaction(transactionId))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(get("/api/transactions/{transactionId}", transactionId))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.status").value(500));

        verify(transactionService).getTransaction(transactionId);
    }

    @Test
    void healthCheck_NoAuthentication_ReturnsOk() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/transactions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("transaction-service"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(transactionService);
    }
}