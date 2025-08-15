package com.suhasan.finance.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.TransferRequest;
import com.suhasan.finance.transaction_service.dto.DepositRequest;
import com.suhasan.finance.transaction_service.dto.WithdrawalRequest;
import com.suhasan.finance.transaction_service.dto.ReversalRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class TransactionControllerTest {

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
    private ReversalRequest reversalRequest;

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

        reversalRequest = ReversalRequest.builder()
                .reason("Customer request")
                .build();
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_Success() throws Exception {
        // Arrange
        when(transactionService.processTransfer(any(TransferRequest.class), eq("user123")))
                .thenReturn(transactionResponse);

        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("txn123"))
                .andExpect(jsonPath("$.type").value("TRANSFER"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(100))
                .andExpect(jsonPath("$.fromAccountId").value("acc1"))
                .andExpect(jsonPath("$.toAccountId").value("acc2"));

        verify(transactionService).processTransfer(any(TransferRequest.class), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_ValidationError() throws Exception {
        // Arrange
        transferRequest.setAmount(null); // Invalid amount

        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    void processTransfer_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void processDeposit_Success() throws Exception {
        // Arrange
        when(transactionService.processDeposit(eq("acc1"), eq(BigDecimal.valueOf(200)), 
                eq("Test deposit"), eq("user123")))
                .thenReturn(transactionResponse);

        // Act & Assert
        mockMvc.perform(post("/api/transactions/deposit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("txn123"));

        verify(transactionService).processDeposit(eq("acc1"), eq(BigDecimal.valueOf(200)), 
                eq("Test deposit"), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void processWithdrawal_Success() throws Exception {
        // Arrange
        when(transactionService.processWithdrawal(eq("acc1"), eq(BigDecimal.valueOf(150)), 
                eq("Test withdrawal"), eq("user123")))
                .thenReturn(transactionResponse);

        // Act & Assert
        mockMvc.perform(post("/api/transactions/withdraw")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withdrawalRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId").value("txn123"));

        verify(transactionService).processWithdrawal(eq("acc1"), eq(BigDecimal.valueOf(150)), 
                eq("Test withdrawal"), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void getTransaction_Success() throws Exception {
        // Arrange
        String transactionId = "txn123";
        when(transactionService.getTransaction(transactionId)).thenReturn(transactionResponse);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/{transactionId}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("txn123"))
                .andExpect(jsonPath("$.type").value("TRANSFER"));

        verify(transactionService).getTransaction(transactionId);
    }

    @Test
    @WithMockUser(username = "user123")
    void getAccountTransactions_Success() throws Exception {
        // Arrange
        String accountId = "acc1";
        List<TransactionResponse> transactions = Arrays.asList(transactionResponse);
        Page<TransactionResponse> page = new PageImpl<>(transactions, PageRequest.of(0, 20), 1);
        
        when(transactionService.getAccountTransactions(eq(accountId), any()))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/account/{accountId}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn123"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(transactionService).getAccountTransactions(eq(accountId), any());
    }

    @Test
    @WithMockUser(username = "user123")
    void getUserTransactions_Success() throws Exception {
        // Arrange
        List<TransactionResponse> transactions = Arrays.asList(transactionResponse);
        Page<TransactionResponse> page = new PageImpl<>(transactions, PageRequest.of(0, 20), 1);
        
        when(transactionService.getUserTransactions(eq("user123"), any()))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn123"));

        verify(transactionService).getUserTransactions(eq("user123"), any());
    }

    @Test
    @WithMockUser(username = "user123")
    void getUserTransactionsAlternative_Success() throws Exception {
        // Arrange
        List<TransactionResponse> transactions = Arrays.asList(transactionResponse);
        Page<TransactionResponse> page = new PageImpl<>(transactions, PageRequest.of(0, 20), 1);
        
        when(transactionService.getUserTransactions(eq("user123"), any()))
                .thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn123"));

        verify(transactionService).getUserTransactions(eq("user123"), any());
    }

    @Test
    @WithMockUser(username = "user123")
    void getTransactionsByStatus_Success() throws Exception {
        // Arrange
        TransactionStatus status = TransactionStatus.COMPLETED;
        List<TransactionResponse> transactions = Arrays.asList(transactionResponse);
        
        when(transactionService.getTransactionsByStatus(status)).thenReturn(transactions);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/status/{status}", status))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].transactionId").value("txn123"))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));

        verify(transactionService).getTransactionsByStatus(status);
    }

    @Test
    @WithMockUser(username = "user123")
    void reverseTransaction_Success() throws Exception {
        // Arrange
        String transactionId = "txn123";
        TransactionResponse reversalResponse = TransactionResponse.builder()
                .transactionId("rev123")
                .type(TransactionType.REVERSAL)
                .status(TransactionStatus.COMPLETED)
                .originalTransactionId(transactionId)
                .build();
        
        when(transactionService.reverseTransaction(eq(transactionId), eq("Customer request"), eq("user123")))
                .thenReturn(reversalResponse);

        // Act & Assert
        mockMvc.perform(post("/api/transactions/{transactionId}/reverse", transactionId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reversalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("rev123"))
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.originalTransactionId").value(transactionId));

        verify(transactionService).reverseTransaction(eq(transactionId), eq("Customer request"), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void isTransactionReversed_True() throws Exception {
        // Arrange
        String transactionId = "txn123";
        when(transactionService.isTransactionReversed(transactionId)).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/{transactionId}/reversed", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.isReversed").value(true));

        verify(transactionService).isTransactionReversed(transactionId);
    }

    @Test
    @WithMockUser(username = "user123")
    void isTransactionReversed_False() throws Exception {
        // Arrange
        String transactionId = "txn123";
        when(transactionService.isTransactionReversed(transactionId)).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/{transactionId}/reversed", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId))
                .andExpect(jsonPath("$.isReversed").value(false));

        verify(transactionService).isTransactionReversed(transactionId);
    }

    @Test
    @WithMockUser(username = "user123")
    void getReversalTransactions_Success() throws Exception {
        // Arrange
        String transactionId = "txn123";
        TransactionResponse reversalResponse = TransactionResponse.builder()
                .transactionId("rev123")
                .type(TransactionType.REVERSAL)
                .originalTransactionId(transactionId)
                .build();
        List<TransactionResponse> reversals = Arrays.asList(reversalResponse);
        
        when(transactionService.getReversalTransactions(transactionId)).thenReturn(reversals);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/{transactionId}/reversals", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].transactionId").value("rev123"))
                .andExpect(jsonPath("$[0].type").value("REVERSAL"));

        verify(transactionService).getReversalTransactions(transactionId);
    }

    @Test
    @WithMockUser(username = "user123")
    void getTransactionLimits_Success() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/transactions/limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyLimit").value(10000.00))
                .andExpect(jsonPath("$.monthlyLimit").value(50000.00))
                .andExpect(jsonPath("$.singleTransactionLimit").value(10000.00))
                .andExpect(jsonPath("$.currency").value("USD"));

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void searchTransactions_Success() throws Exception {
        // Arrange
        List<TransactionResponse> transactions = Arrays.asList(transactionResponse);
        Page<TransactionResponse> page = new PageImpl<>(transactions, PageRequest.of(0, 20), 1);
        
        when(transactionService.searchTransactions(any(), any())).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/search")
                .param("accountId", "acc1")
                .param("type", "TRANSFER")
                .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].transactionId").value("txn123"));

        verify(transactionService).searchTransactions(any(), any());
    }

    @Test
    void healthCheck_Success() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/transactions/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("transaction-service"))
                .andExpect(jsonPath("$.timestamp").exists());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_ServiceException() throws Exception {
        // Arrange
        when(transactionService.processTransfer(any(TransferRequest.class), eq("user123")))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isInternalServerError());

        verify(transactionService).processTransfer(any(TransferRequest.class), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void processTransfer_IllegalArgumentException() throws Exception {
        // Arrange
        when(transactionService.processTransfer(any(TransferRequest.class), eq("user123")))
                .thenThrow(new IllegalArgumentException("Invalid account"));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transferRequest)))
                .andExpect(status().isBadRequest());

        verify(transactionService).processTransfer(any(TransferRequest.class), eq("user123"));
    }

    @Test
    @WithMockUser(username = "user123")
    void getTransaction_NotFound() throws Exception {
        // Arrange
        String transactionId = "nonexistent";
        when(transactionService.getTransaction(transactionId))
                .thenThrow(new IllegalArgumentException("Transaction not found"));

        // Act & Assert
        mockMvc.perform(get("/api/transactions/{transactionId}", transactionId))
                .andExpect(status().isBadRequest());

        verify(transactionService).getTransaction(transactionId);
    }

    @Test
    @WithMockUser(username = "user123")
    void processDeposit_ValidationError_NegativeAmount() throws Exception {
        // Arrange
        depositRequest.setAmount(BigDecimal.valueOf(-100)); // Invalid negative amount

        // Act & Assert
        mockMvc.perform(post("/api/transactions/deposit")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(depositRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void processWithdrawal_ValidationError_NullAccountId() throws Exception {
        // Arrange
        withdrawalRequest.setAccountId(null); // Invalid null account ID

        // Act & Assert
        mockMvc.perform(post("/api/transactions/withdraw")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(withdrawalRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(username = "user123")
    void reverseTransaction_ValidationError_EmptyReason() throws Exception {
        // Arrange
        String transactionId = "txn123";
        reversalRequest.setReason(""); // Invalid empty reason

        // Act & Assert
        mockMvc.perform(post("/api/transactions/{transactionId}/reverse", transactionId)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reversalRequest)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(transactionService);
    }
}