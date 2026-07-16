package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapCommand;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapResult;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapCoordinator;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapPreflightService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminLedgerBootstrapController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import(SecurityConfig.class)
class AdminLedgerBootstrapControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private LedgerBootstrapCoordinator bootstrapCoordinator;
    @MockitoBean private LedgerBootstrapPreflightService preflightService;

    @BeforeEach
    void allowJwtFilterToContinue() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void bootstrapCommandRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/ledger/bootstrap"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void bootstrapCommandRejectsCustomerUsers() throws Exception {
        mockMvc.perform(post("/api/admin/ledger/bootstrap")
                        .contentType("application/json")
                        .header("X-Operator-Request-Id", "deploy-123")
                        .content("{\"enabled\":true,\"maintenanceMode\":true,\"businessDate\":\"2026-06-26\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "service", roles = "INTERNAL_SERVICE")
    void bootstrapCommandRejectsInternalServiceIdentity() throws Exception {
        mockMvc.perform(post("/api/admin/ledger/bootstrap")
                        .contentType("application/json")
                        .header("X-Operator-Request-Id", "deploy-123")
                        .content("{\"enabled\":true,\"maintenanceMode\":true,\"businessDate\":\"2026-06-26\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void preflightRequiresCorrelatedRequestEvidence() throws Exception {
        mockMvc.perform(get("/api/admin/ledger/bootstrap/preflight")
                        .param("maintenanceMode", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void adminCanRunBootstrapCommandWithMaintenanceMode() throws Exception {
        when(bootstrapCoordinator.bootstrap(new LedgerBootstrapCommand("ops", "ROLE_ADMIN", "deploy-123", true, true, LocalDate.parse("2026-06-26")), "ADMIN_API"))
                .thenReturn(new LedgerBootstrapResult(2, 1, 6, 2, List.of("USD", "EUR")));

        mockMvc.perform(post("/api/admin/ledger/bootstrap")
                        .contentType("application/json")
                        .header("X-Operator-Request-Id", "deploy-123")
                        .content("{\"enabled\":true,\"maintenanceMode\":true,\"businessDate\":\"2026-06-26\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedAccounts").value(2))
                .andExpect(jsonPath("$.reusedAccounts").value(1))
                .andExpect(jsonPath("$.seededSystemAccounts").value(6))
                .andExpect(jsonPath("$.openingJournals").value(2))
                .andExpect(jsonPath("$.currencies[0]").value("USD"));

        verify(bootstrapCoordinator).bootstrap(new LedgerBootstrapCommand("ops", "ROLE_ADMIN", "deploy-123", true, true, LocalDate.parse("2026-06-26")), "ADMIN_API");
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void bootstrapPreflightFailuresReturnConflict() throws Exception {
        when(bootstrapCoordinator.bootstrap(any(LedgerBootstrapCommand.class), eq("ADMIN_API")))
                .thenThrow(new IllegalStateException("Ledger bootstrap blocked by 1 unresolved legacy holds"));

        mockMvc.perform(post("/api/admin/ledger/bootstrap")
                        .contentType("application/json")
                        .header("X-Operator-Request-Id", "deploy-123")
                        .content("{\"enabled\":true,\"maintenanceMode\":true,\"businessDate\":\"2026-06-26\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ledger bootstrap blocked by 1 unresolved legacy holds"));
    }
}
