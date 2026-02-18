package com.suhasan.finance.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.ReversalRequest;
import com.suhasan.finance.transaction_service.dto.TransactionResponse;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.exception.TransactionAlreadyReversedException;
import com.suhasan.finance.transaction_service.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
@SuppressWarnings("null")
class TransactionControllerReversalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    @WithMockUser(username = "admin-user")
    void testReverseTransaction_Success() throws Exception {
        // Arrange
        ReversalRequest request = ReversalRequest.builder()
                .reason("Customer request for refund")
                .reference("REF-123")
                .build();

        TransactionResponse response = TransactionResponse.builder()
                .transactionId("reversal-tx-456")
                .fromAccountId("account-2")
                .toAccountId("account-1")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .type(TransactionType.REVERSAL)
                .status(TransactionStatus.COMPLETED)
                .description("Reversal of transaction original-tx-123: Customer request for refund")
                .reference("REV-original-tx-123")
                .createdAt(LocalDateTime.now())
                .processedAt(LocalDateTime.now())
                .createdBy("admin-user")
                .originalTransactionId("original-tx-123")
                .reversalReason("Customer request for refund")
                .build();

        when(transactionService.reverseTransaction("original-tx-123", "Customer request for refund", "admin-user"))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/transactions/original-tx-123/reverse")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("reversal-tx-456"))
                .andExpect(jsonPath("$.type").value("REVERSAL"))
                .andExpect(jsonPath("$.originalTransactionId").value("original-tx-123"))
                .andExpect(jsonPath("$.reversalReason").value("Customer request for refund"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(transactionService).reverseTransaction("original-tx-123", "Customer request for refund", "admin-user");
    }

    @Test
    @WithMockUser(username = "admin-user")
    void testReverseTransaction_AlreadyReversed() throws Exception {
        // Arrange
        ReversalRequest request = ReversalRequest.builder()
                .reason("Customer request")
                .build();

        when(transactionService.reverseTransaction("original-tx-123", "Customer request", "admin-user"))
                .thenThrow(new TransactionAlreadyReversedException("original-tx-123"));

        // Act & Assert
        mockMvc.perform(post("/api/transactions/original-tx-123/reverse")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(transactionService).reverseTransaction("original-tx-123", "Customer request", "admin-user");
    }

    @Test
    @WithMockUser(username = "admin-user")
    void testReverseTransaction_InvalidRequest() throws Exception {
        // Arrange - empty reason should fail validation
        ReversalRequest request = ReversalRequest.builder()
                .reason("")
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/transactions/original-tx-123/reverse")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(transactionService, never()).reverseTransaction(anyString(), anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "admin-user")
    void testIsTransactionReversed_True() throws Exception {
        // Arrange
        when(transactionService.isTransactionReversed("tx-123")).thenReturn(true);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/tx-123/reversed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("tx-123"))
                .andExpect(jsonPath("$.isReversed").value(true));

        verify(transactionService).isTransactionReversed("tx-123");
    }

    @Test
    @WithMockUser(username = "admin-user")
    void testIsTransactionReversed_False() throws Exception {
        // Arrange
        when(transactionService.isTransactionReversed("tx-456")).thenReturn(false);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/tx-456/reversed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("tx-456"))
                .andExpect(jsonPath("$.isReversed").value(false));

        verify(transactionService).isTransactionReversed("tx-456");
    }

    @Test
    @WithMockUser(username = "admin-user")
    void testGetReversalTransactions() throws Exception {
        // Arrange
        TransactionResponse reversal1 = TransactionResponse.builder()
                .transactionId("reversal-1")
                .type(TransactionType.REVERSAL)
                .originalTransactionId("original-tx-123")
                .amount(new BigDecimal("50.00"))
                .status(TransactionStatus.COMPLETED)
                .build();

        TransactionResponse reversal2 = TransactionResponse.builder()
                .transactionId("reversal-2")
                .type(TransactionType.REVERSAL)
                .originalTransactionId("original-tx-123")
                .amount(new BigDecimal("25.00"))
                .status(TransactionStatus.COMPLETED)
                .build();

        List<TransactionResponse> reversals = Arrays.asList(reversal1, reversal2);
        when(transactionService.getReversalTransactions("original-tx-123")).thenReturn(reversals);

        // Act & Assert
        mockMvc.perform(get("/api/transactions/original-tx-123/reversals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].transactionId").value("reversal-1"))
                .andExpect(jsonPath("$[0].type").value("REVERSAL"))
                .andExpect(jsonPath("$[0].originalTransactionId").value("original-tx-123"))
                .andExpect(jsonPath("$[1].transactionId").value("reversal-2"));

        verify(transactionService).getReversalTransactions("original-tx-123");
    }

    @Test
    void testReverseTransaction_Unauthorized() throws Exception {
        // Arrange
        ReversalRequest request = ReversalRequest.builder()
                .reason("Customer request")
                .build();

        // Act & Assert - no authentication
        mockMvc.perform(post("/api/transactions/original-tx-123/reverse")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(transactionService, never()).reverseTransaction(anyString(), anyString(), anyString());
    }
}
