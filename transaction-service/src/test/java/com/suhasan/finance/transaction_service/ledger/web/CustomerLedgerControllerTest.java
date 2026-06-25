package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.security.JwtAuthenticationFilter;
import com.suhasan.finance.transaction_service.security.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CustomerLedgerController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import(SecurityConfig.class)
class CustomerLedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomerLedgerQueryService customerLedgerQueryService;

    @BeforeEach
    void allowJwtFilterToContinue() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = "customer-1", roles = "USER")
    void listAccountsReturnsOnlyAuthenticatedCustomersLedgerAccountsInStableOrder() throws Exception {
        when(customerLedgerQueryService.listAccounts("customer-1")).thenReturn(List.of(
                new LedgerAccountSummaryResponse("1001", "USD",
                        new BigDecimal("125.00"), new BigDecimal("-25.00"), new BigDecimal("100.00"),
                        3L, LocalDateTime.parse("2026-06-25T03:30:00")),
                new LedgerAccountSummaryResponse("2002", "USD",
                        new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"),
                        1L, LocalDateTime.parse("2026-06-25T03:31:00"))));

        mockMvc.perform(get("/api/ledger/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalAccountId").value("1001"))
                .andExpect(jsonPath("$[0].postedBalance").value(125.00))
                .andExpect(jsonPath("$[0].pendingBalance").value(-25.00))
                .andExpect(jsonPath("$[0].availableBalance").value(100.00))
                .andExpect(jsonPath("$[0].projectionVersion").value(3))
                .andExpect(jsonPath("$[1].externalAccountId").value("2002"));

        verify(customerLedgerQueryService).listAccounts("customer-1");
    }

    @Test
    @WithMockUser(username = "customer-1", roles = "USER")
    void getBalanceReturnsNotFoundForAccountsOutsideAuthenticatedCustomerOwnership() throws Exception {
        when(customerLedgerQueryService.getBalance("customer-1", "other-account"))
                .thenThrow(new LedgerAccountNotFoundException("Ledger account not found"));

        mockMvc.perform(get("/api/ledger/accounts/other-account/balance"))
                .andExpect(status().isNotFound());

        verify(customerLedgerQueryService).getBalance("customer-1", "other-account");
    }

    @Test
    @WithMockUser(username = "customer-1", roles = "USER")
    void batchBalancesReturnsBalancesInRequestedOrder() throws Exception {
        when(customerLedgerQueryService.getBalances("customer-1", List.of("2002", "1001"))).thenReturn(List.of(
                new LedgerAccountSummaryResponse("2002", "USD",
                        new BigDecimal("50.00"), BigDecimal.ZERO, new BigDecimal("50.00"),
                        1L, LocalDateTime.parse("2026-06-25T03:31:00")),
                new LedgerAccountSummaryResponse("1001", "USD",
                        new BigDecimal("125.00"), new BigDecimal("-25.00"), new BigDecimal("100.00"),
                        3L, LocalDateTime.parse("2026-06-25T03:30:00"))));

        mockMvc.perform(post("/api/ledger/accounts/balances:batch")
                        .contentType("application/json")
                        .content("""
                                {
                                  "externalAccountIds": ["2002", "1001"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalAccountId").value("2002"))
                .andExpect(jsonPath("$[1].externalAccountId").value("1001"));

        verify(customerLedgerQueryService).getBalances("customer-1", List.of("2002", "1001"));
    }

    @Test
    @WithMockUser(username = "customer-1", roles = "USER")
    void getJournalReturnsCustomerSafeViewWithoutInternalLedgerAccountIds() throws Exception {
        UUID journalId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(customerLedgerQueryService.getJournal("customer-1", journalId)).thenReturn(
                new CustomerJournalResponse(
                        journalId,
                        "JRN-aaaaaaaa",
                        "TRANSFER",
                        "POSTED",
                        "USD",
                        new BigDecimal("25.00"),
                        "Transfer to savings",
                        LocalDateTime.parse("2026-06-25T03:35:00"),
                        null,
                        List.of(new CustomerJournalPostingResponse(
                                "1001",
                                "DEBIT",
                                new BigDecimal("25.00"),
                                "USD",
                                "Transfer to savings"))));

        mockMvc.perform(get("/api/ledger/journals/{journalId}", journalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.journalId").value(journalId.toString()))
                .andExpect(jsonPath("$.journalReference").value("JRN-aaaaaaaa"))
                .andExpect(jsonPath("$.state").value("POSTED"))
                .andExpect(jsonPath("$.postings[0].externalAccountId").value("1001"))
                .andExpect(jsonPath("$.postings[0].ledgerAccountId").doesNotExist());

        verify(customerLedgerQueryService).getJournal("customer-1", journalId);
    }

    @Test
    @WithMockUser(username = "customer-1", roles = "USER")
    void getJournalRejectsSystemAccountExposureForCustomers() throws Exception {
        UUID journalId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(customerLedgerQueryService.getJournal("customer-1", journalId))
                .thenThrow(new AccessDeniedException("System ledger postings require elevated access"));

        mockMvc.perform(get("/api/ledger/journals/{journalId}", journalId))
                .andExpect(status().isForbidden());
    }

    @Test
    void ledgerApisRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/ledger/accounts"))
                .andExpect(status().isUnauthorized());
    }
}
