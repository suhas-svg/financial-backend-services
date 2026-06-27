package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapCommand;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapResult;
import com.suhasan.finance.transaction_service.ledger.service.LedgerBootstrapService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminLedgerBootstrapController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import(SecurityConfig.class)
class AdminLedgerBootstrapControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private LedgerBootstrapService bootstrapService;

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
                        .content("{\"enabled\":true,\"maintenanceMode\":true,\"businessDate\":\"2026-06-26\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void adminCanRunBootstrapCommandWithMaintenanceMode() throws Exception {
        when(bootstrapService.bootstrap(new LedgerBootstrapCommand("ops", true, true, LocalDate.parse("2026-06-26"))))
                .thenReturn(new LedgerBootstrapResult(2, 1, 6, 2, List.of("USD", "EUR")));

        mockMvc.perform(post("/api/admin/ledger/bootstrap")
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"maintenanceMode\":true,\"businessDate\":\"2026-06-26\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedAccounts").value(2))
                .andExpect(jsonPath("$.reusedAccounts").value(1))
                .andExpect(jsonPath("$.seededSystemAccounts").value(6))
                .andExpect(jsonPath("$.openingJournals").value(2))
                .andExpect(jsonPath("$.currencies[0]").value("USD"));

        verify(bootstrapService).bootstrap(new LedgerBootstrapCommand("ops", true, true, LocalDate.parse("2026-06-26")));
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void bootstrapPreflightFailuresReturnConflict() throws Exception {
        when(bootstrapService.bootstrap(any(LedgerBootstrapCommand.class)))
                .thenThrow(new IllegalStateException("Ledger bootstrap blocked by 1 unresolved legacy holds"));

        mockMvc.perform(post("/api/admin/ledger/bootstrap")
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"maintenanceMode\":true,\"businessDate\":\"2026-06-26\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ledger bootstrap blocked by 1 unresolved legacy holds"));
    }
}
