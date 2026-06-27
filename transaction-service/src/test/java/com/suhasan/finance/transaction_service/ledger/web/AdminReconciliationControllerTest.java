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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminReconciliationController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import(SecurityConfig.class)
class AdminReconciliationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private AdminReconciliationQueryService reconciliationQueryService;

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
    void reconciliationApisRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/reconciliation/runs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void reconciliationApisRejectCustomerUsers() throws Exception {
        mockMvc.perform(get("/api/admin/reconciliation/runs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void adminCanTriggerManualRunAndReceiveSummary() throws Exception {
        when(reconciliationQueryService.runDaily(LocalDate.of(2026, 6, 24), "ops"))
                .thenReturn(new ReconciliationRunResponse(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        LocalDate.of(2026, 6, 24),
                        "DAILY_LEDGER",
                        "COMPLETED_WITH_EXCEPTIONS",
                        2,
                        1));

        mockMvc.perform(post("/api/admin/reconciliation/runs")
                        .contentType("application/json")
                        .content("{\"businessDate\":\"2026-06-24\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_EXCEPTIONS"))
                .andExpect(jsonPath("$.criticalExceptions").value(1));
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void adminCanListAndResolveExceptionsWithOptimisticVersion() throws Exception {
        UUID exceptionId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(reconciliationQueryService.listExceptions("OPEN", "CRITICAL"))
                .thenReturn(List.of(new ReconciliationExceptionResponse(
                        exceptionId,
                        "PROJECTION_RECOMPUTATION",
                        "CRITICAL",
                        "OPEN",
                        "projection:account-1:posted-balance",
                        "Projection drift",
                        "ops",
                        null,
                        List.of(),
                        3L)));
        when(reconciliationQueryService.updateStatus(
                exceptionId, "RESOLVED", "Corrected through compensating journal", "ops", 3L))
                .thenReturn(new ReconciliationExceptionResponse(
                        exceptionId,
                        "PROJECTION_RECOMPUTATION",
                        "CRITICAL",
                        "RESOLVED",
                        "projection:account-1:posted-balance",
                        "Projection drift",
                        "ops",
                        "Corrected through compensating journal",
                        List.of(new ReconciliationExceptionNoteResponse(
                                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                                "ops",
                                "Corrected through compensating journal")),
                        4L));

        mockMvc.perform(get("/api/admin/reconciliation/exceptions")
                        .param("status", "OPEN")
                        .param("severity", "CRITICAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].exceptionId").value(exceptionId.toString()))
                .andExpect(jsonPath("$[0].fingerprint").value("projection:account-1:posted-balance"));

        mockMvc.perform(patch("/api/admin/reconciliation/exceptions/{exceptionId}/status", exceptionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "RESOLVED",
                                  "note": "Corrected through compensating journal",
                                  "expectedVersion": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNote").value("Corrected through compensating journal"))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void adminCanAssignAndAddExceptionNotesWithOptimisticVersion() throws Exception {
        UUID exceptionId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        UUID noteId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        when(reconciliationQueryService.assignException(exceptionId, "analyst-1", "ops", 2L))
                .thenReturn(new ReconciliationExceptionResponse(
                        exceptionId,
                        "JOURNAL_BALANCE_BY_CURRENCY",
                        "CRITICAL",
                        "IN_PROGRESS",
                        "journal:j1:USD:journal-balance",
                        "Journal out of balance",
                        "analyst-1",
                        null,
                        List.of(),
                        3L));
        when(reconciliationQueryService.addNote(exceptionId, "Investigating source journal", "ops"))
                .thenReturn(new ReconciliationExceptionResponse(
                        exceptionId,
                        "JOURNAL_BALANCE_BY_CURRENCY",
                        "CRITICAL",
                        "IN_PROGRESS",
                        "journal:j1:USD:journal-balance",
                        "Journal out of balance",
                        "analyst-1",
                        null,
                        List.of(new ReconciliationExceptionNoteResponse(
                                noteId,
                                "ops",
                                "Investigating source journal")),
                        3L));

        mockMvc.perform(patch("/api/admin/reconciliation/exceptions/{exceptionId}/assignment", exceptionId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "assignedTo": "analyst-1",
                                  "expectedVersion": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value("analyst-1"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(3));

        mockMvc.perform(post("/api/admin/reconciliation/exceptions/{exceptionId}/notes", exceptionId)
                        .contentType("application/json")
                        .content("{\"note\":\"Investigating source journal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].noteId").value(noteId.toString()))
                .andExpect(jsonPath("$.notes[0].author").value("ops"))
                .andExpect(jsonPath("$.notes[0].note").value("Investigating source journal"));
    }
}
