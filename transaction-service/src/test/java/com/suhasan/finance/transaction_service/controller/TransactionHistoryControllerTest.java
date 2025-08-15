package com.suhasan.finance.transaction_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhasan.finance.transaction_service.dto.TransactionStatsResponse;
import com.suhasan.finance.transaction_service.entity.TransactionStatus;
import com.suhasan.finance.transaction_service.entity.TransactionType;
import com.suhasan.finance.transaction_service.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
class TransactionHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "test-user")
    void testSearchTransactions() throws Exception {
        // Given
        Page<com.suhasan.finance.transaction_service.dto.TransactionResponse> mockPage = 
            new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        
        when(transactionService.searchTransactions(any(), any())).thenReturn(mockPage);

        // When & Then
        mockMvc.perform(get("/api/transactions/search")
                .param("accountId", "account-123")
                .param("type", "TRANSFER")
                .param("status", "COMPLETED")
                .param("minAmount", "100.00")
                .param("maxAmount", "1000.00")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = "test-user")
    void testGetAccountTransactionStats() throws Exception {
        // Given
        TransactionStatsResponse mockStats = TransactionStatsResponse.builder()
                .accountId("account-123")
                .totalTransactions(10L)
                .completedTransactions(8L)
                .pendingTransactions(1L)
                .failedTransactions(1L)
                .reversedTransactions(0L)
                .totalAmount(BigDecimal.valueOf(5000.00))
                .totalIncoming(BigDecimal.valueOf(3000.00))
                .totalOutgoing(BigDecimal.valueOf(2000.00))
                .averageTransactionAmount(BigDecimal.valueOf(500.00))
                .successRate(80.0)
                .currency("USD")
                .transactionCountsByType(Map.of(
                    "DEPOSIT", 3L,
                    "WITHDRAWAL", 2L,
                    "TRANSFER", 5L
                ))
                .transactionAmountsByType(Map.of(
                    "DEPOSIT", BigDecimal.valueOf(2000.00),
                    "WITHDRAWAL", BigDecimal.valueOf(1000.00),
                    "TRANSFER", BigDecimal.valueOf(2000.00)
                ))
                .build();

        when(transactionService.getAccountTransactionStats(eq("account-123"), any(), any()))
                .thenReturn(mockStats);

        // When & Then
        mockMvc.perform(get("/api/transactions/account/account-123/stats")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("account-123"))
                .andExpect(jsonPath("$.totalTransactions").value(10))
                .andExpect(jsonPath("$.completedTransactions").value(8))
                .andExpect(jsonPath("$.totalAmount").value(5000.00))
                .andExpect(jsonPath("$.successRate").value(80.0))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    @WithMockUser(username = "test-user")
    void testGetUserTransactionStats() throws Exception {
        // Given
        TransactionStatsResponse mockStats = TransactionStatsResponse.builder()
                .totalTransactions(5L)
                .completedTransactions(4L)
                .totalAmount(BigDecimal.valueOf(2500.00))
                .successRate(80.0)
                .currency("USD")
                .build();

        when(transactionService.getUserTransactionStats(eq("test-user"), any(), any()))
                .thenReturn(mockStats);

        // When & Then
        mockMvc.perform(get("/api/transactions/user/stats")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").value(5))
                .andExpect(jsonPath("$.completedTransactions").value(4))
                .andExpect(jsonPath("$.totalAmount").value(2500.00))
                .andExpect(jsonPath("$.successRate").value(80.0));
    }

    @Test
    @WithMockUser(username = "test-user")
    void testSearchTransactionsWithDateRange() throws Exception {
        // Given
        Page<com.suhasan.finance.transaction_service.dto.TransactionResponse> mockPage = 
            new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        
        when(transactionService.searchTransactions(any(), any())).thenReturn(mockPage);

        // When & Then
        mockMvc.perform(get("/api/transactions/search")
                .param("startDate", "2024-01-01T00:00:00")
                .param("endDate", "2024-12-31T23:59:59")
                .param("description", "test payment")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(username = "test-user")
    void testGetAccountStatsWithDateRange() throws Exception {
        // Given
        TransactionStatsResponse mockStats = TransactionStatsResponse.builder()
                .accountId("account-123")
                .periodStart(LocalDateTime.of(2024, 1, 1, 0, 0))
                .periodEnd(LocalDateTime.of(2024, 12, 31, 23, 59))
                .totalTransactions(15L)
                .completedTransactions(12L)
                .totalAmount(BigDecimal.valueOf(7500.00))
                .successRate(80.0)
                .currency("USD")
                .build();

        when(transactionService.getAccountTransactionStats(eq("account-123"), any(), any()))
                .thenReturn(mockStats);

        // When & Then
        mockMvc.perform(get("/api/transactions/account/account-123/stats")
                .param("startDate", "2024-01-01T00:00:00")
                .param("endDate", "2024-12-31T23:59:59")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("account-123"))
                .andExpect(jsonPath("$.totalTransactions").value(15))
                .andExpect(jsonPath("$.totalAmount").value(7500.00));
    }
}