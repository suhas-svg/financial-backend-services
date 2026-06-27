package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.dto.AccountResponse;
import com.suhasan.finance.account_service.dto.LedgerProjectionUpdateRequest;
import com.suhasan.finance.account_service.security.CustomUserDetailsService;
import com.suhasan.finance.account_service.security.JwtAuthenticationFilter;
import com.suhasan.finance.account_service.security.JwtTokenProvider;
import com.suhasan.finance.account_service.security.SecurityConfig;
import com.suhasan.finance.account_service.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalAccountController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@Execution(ExecutionMode.SAME_THREAD)
class InternalLedgerProjectionControllerTest {

    private static final String BODY = """
            {
              "postedBalance": 125.00,
              "pendingBalance": -20.00,
              "availableBalance": 105.00,
              "currency": "USD",
              "version": 7,
              "sourceEventId": "event-7",
              "updatedAt": "2026-06-24T12:00:00"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void unauthenticatedProjectionUpdateReturns401() throws Exception {
        mockMvc.perform(put("/api/internal/accounts/1/ledger-projection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void customerProjectionUpdateReturns403() throws Exception {
        mockMvc.perform(put("/api/internal/accounts/1/ledger-projection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "transaction-service", roles = "INTERNAL_SERVICE")
    void internalServiceCanUpdateProjection() throws Exception {
        AccountResponse response = new AccountResponse();
        response.setId(1L);
        response.setCurrency("USD");
        response.setLedgerBalance(new java.math.BigDecimal("125.00"));
        response.setPendingBalance(new java.math.BigDecimal("-20.00"));
        response.setAvailableBalance(new java.math.BigDecimal("105.00"));
        response.setLedgerProjectionVersion(7L);
        when(accountService.applyLedgerProjection(eq(1L), org.mockito.ArgumentMatchers.any(LedgerProjectionUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/internal/accounts/1/ledger-projection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.ledgerProjectionVersion").value(7));

        verify(accountService).applyLedgerProjection(eq(1L),
                org.mockito.ArgumentMatchers.any(LedgerProjectionUpdateRequest.class));
    }
}
