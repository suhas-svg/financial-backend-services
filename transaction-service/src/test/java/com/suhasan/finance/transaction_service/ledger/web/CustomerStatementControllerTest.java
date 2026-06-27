package com.suhasan.finance.transaction_service.ledger.web;

import com.suhasan.finance.transaction_service.ledger.service.CustomerMonthlyStatementLineResult;
import com.suhasan.finance.transaction_service.ledger.service.CustomerMonthlyStatementResult;
import com.suhasan.finance.transaction_service.ledger.service.MonthlyStatementService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = CustomerStatementController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import(SecurityConfig.class)
class CustomerStatementControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private MonthlyStatementService statementService;

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
    void statementApisRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/ledger/statements"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer-1", roles = "USER")
    void customerCanListGenerateAndReadOnlyOwnStatements() throws Exception {
        UUID statementId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        CustomerMonthlyStatementResult statement = statement(statementId);
        when(statementService.listForOwner("customer-1")).thenReturn(List.of(statement));
        when(statementService.generate("customer-1", "1001", YearMonth.of(2026, 5))).thenReturn(statement);
        when(statementService.getForOwner("customer-1", statementId)).thenReturn(statement);

        mockMvc.perform(get("/api/ledger/statements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statementId").value(statementId.toString()))
                .andExpect(jsonPath("$[0].externalAccountId").value("1001"))
                .andExpect(jsonPath("$[0].openingBalance").value(1000.00))
                .andExpect(jsonPath("$[0].closingBalance").value(985.00));

        mockMvc.perform(post("/api/ledger/statements")
                        .contentType("application/json")
                        .content("""
                                {
                                  "externalAccountId": "1001",
                                  "yearMonth": "2026-05"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodStart").value("2026-05-01"))
                .andExpect(jsonPath("$.periodEnd").value("2026-06-01"))
                .andExpect(jsonPath("$.lines[0].description").value("ATM withdrawal"));

        mockMvc.perform(get("/api/ledger/statements/{statementId}", statementId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statementId").value(statementId.toString()));

        verify(statementService).listForOwner("customer-1");
        verify(statementService).generate("customer-1", "1001", YearMonth.of(2026, 5));
        verify(statementService).getForOwner("customer-1", statementId);
    }

    @Test
    @WithMockUser(username = "customer-1", roles = "USER")
    void csvExportUsesAttachmentHeadersAndEscapesCustomerStatementLines() throws Exception {
        UUID statementId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(statementService.exportCsvForOwner("customer-1", statementId))
                .thenReturn("statementId,externalAccountId,periodStart,periodEnd,lineDate,description,amount,runningBalance,currency\n"
                        + "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb,1001,2026-05-01,2026-06-01,2026-05-10,\"ATM \"\"Main, Branch\"\"\",-25.00,975.00,USD\n");

        mockMvc.perform(get("/api/ledger/statements/{statementId}/csv", statementId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"statement-bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.csv\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ATM \"\"Main, Branch\"\"")));

        verify(statementService).exportCsvForOwner("customer-1", statementId);
    }

    @Test
    @WithMockUser(username = "customer-1", roles = "USER")
    void statementDetailRejectsCrossCustomerAccess() throws Exception {
        UUID statementId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(statementService.getForOwner("customer-1", statementId))
                .thenThrow(new AccessDeniedException("Statement belongs to another customer"));

        mockMvc.perform(get("/api/ledger/statements/{statementId}", statementId))
                .andExpect(status().isForbidden());
    }

    private CustomerMonthlyStatementResult statement(UUID statementId) {
        return new CustomerMonthlyStatementResult(
                statementId,
                "customer-1",
                "1001",
                "USD",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 1),
                1,
                new BigDecimal("1000.00"),
                new BigDecimal("985.00"),
                LocalDateTime.parse("2026-06-01T00:00:00"),
                List.of(new CustomerMonthlyStatementLineResult(
                        UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
                        UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                        1,
                        LocalDate.of(2026, 5, 10),
                        "ATM withdrawal",
                        new BigDecimal("-25.00"),
                        new BigDecimal("975.00"),
                        "USD")));
    }
}
