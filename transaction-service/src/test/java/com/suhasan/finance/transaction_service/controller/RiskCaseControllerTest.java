package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.RiskCaseCreateRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseResponse;
import com.suhasan.finance.transaction_service.dto.RiskCaseStatusUpdateRequest;
import com.suhasan.finance.transaction_service.dto.RiskCaseSummaryResponse;
import com.suhasan.finance.transaction_service.dto.RiskCaseNoteRequest;
import com.suhasan.finance.transaction_service.entity.RiskCasePriority;
import com.suhasan.finance.transaction_service.entity.RiskCaseStatus;
import com.suhasan.finance.transaction_service.security.JwtAuthenticationFilter;
import com.suhasan.finance.transaction_service.service.RiskCaseService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskCaseController.class)
@Import(com.suhasan.finance.transaction_service.security.SecurityConfig.class)
@EnableWebSecurity
class RiskCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiskCaseService riskCaseService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUpFilter() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listCases_AllowsAdminAndPassesFilters() throws Exception {
        RiskCaseResponse response = RiskCaseResponse.builder()
                .caseId("case-1")
                .caseNumber("RC-20260509-0001")
                .status(RiskCaseStatus.OPEN)
                .priority(RiskCasePriority.HIGH)
                .title("Review high-value transfer")
                .userId("user-1")
                .transactionId("txn-1")
                .primaryAlertId("alert-1")
                .createdAt(LocalDateTime.parse("2026-05-09T10:15:30"))
                .updatedAt(LocalDateTime.parse("2026-05-09T10:15:30"))
                .build();
        Page<RiskCaseResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);
        when(riskCaseService.searchCases(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/risk/cases")
                        .param("status", "OPEN")
                        .param("priority", "HIGH")
                        .param("assignedTo", "ops")
                        .param("userId", "user-1")
                        .param("transactionId", "txn-1")
                        .param("alertId", "alert-1")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-10T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].caseId").value("case-1"))
                .andExpect(jsonPath("$.content[0].caseNumber").value("RC-20260509-0001"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(riskCaseService).searchCases(argThat(filter ->
                        filter.getStatus() == RiskCaseStatus.OPEN
                                && filter.getPriority() == RiskCasePriority.HIGH
                                && "ops".equals(filter.getAssignedTo())
                                && "user-1".equals(filter.getUserId())
                                && "txn-1".equals(filter.getTransactionId())
                                && "alert-1".equals(filter.getAlertId())
                                && LocalDateTime.parse("2026-05-01T00:00:00").equals(filter.getFrom())
                                && LocalDateTime.parse("2026-05-10T00:00:00").equals(filter.getTo())),
                any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listCases_RejectsNormalUsers() throws Exception {
        mockMvc.perform(get("/api/risk/cases"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void createCaseFromAlert_UsesCurrentAdmin() throws Exception {
        RiskCaseResponse response = RiskCaseResponse.builder()
                .caseId("case-1")
                .caseNumber("RC-20260509-0001")
                .status(RiskCaseStatus.OPEN)
                .priority(RiskCasePriority.HIGH)
                .title("Review high-value transfer")
                .createdBy("ops")
                .primaryAlertId("alert-1")
                .build();
        when(riskCaseService.createFromAlert(eq("alert-1"), any(RiskCaseCreateRequest.class), eq("ops"))).thenReturn(response);

        mockMvc.perform(post("/api/risk/cases/from-alert/alert-1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Review high-value transfer",
                                  "priority": "HIGH",
                                  "reason": "Manual review requested by admin"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("case-1"))
                .andExpect(jsonPath("$.createdBy").value("ops"))
                .andExpect(jsonPath("$.primaryAlertId").value("alert-1"));

        verify(riskCaseService).createFromAlert(eq("alert-1"), argThat(request ->
                "Review high-value transfer".equals(request.getTitle())
                        && request.getPriority() == RiskCasePriority.HIGH
                        && "Manual review requested by admin".equals(request.getReason())), eq("ops"));
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void claimCase_AssignsCurrentAdmin() throws Exception {
        RiskCaseResponse response = RiskCaseResponse.builder()
                .caseId("case-1")
                .assignedTo("ops")
                .status(RiskCaseStatus.IN_REVIEW)
                .claimedAt(LocalDateTime.parse("2026-05-09T10:30:00"))
                .build();
        when(riskCaseService.claimCase("case-1", "ops")).thenReturn(response);

        mockMvc.perform(patch("/api/risk/cases/case-1/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").value("ops"))
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.claimedAt").value("2026-05-09T10:30:00"));
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void updateCaseStatus_PersistsResolutionNote() throws Exception {
        RiskCaseResponse response = RiskCaseResponse.builder()
                .caseId("case-1")
                .status(RiskCaseStatus.RESOLVED)
                .resolutionNote("Reviewed and resolved")
                .closedAt(LocalDateTime.parse("2026-05-09T11:00:00"))
                .build();
        when(riskCaseService.updateStatus(eq("case-1"), any(RiskCaseStatusUpdateRequest.class), eq("ops"))).thenReturn(response);

        mockMvc.perform(patch("/api/risk/cases/case-1/status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "RESOLVED",
                                  "resolutionNote": "Reviewed and resolved"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolutionNote").value("Reviewed and resolved"))
                .andExpect(jsonPath("$.closedAt").value("2026-05-09T11:00:00"));
    }

    @Test
    @WithMockUser(username = "ops", roles = "ADMIN")
    void addNote_AppendsInternalNote() throws Exception {
        RiskCaseResponse response = RiskCaseResponse.builder()
                .caseId("case-1")
                .notes(List.of(RiskCaseResponse.Note.builder()
                        .noteId("note-1")
                        .author("ops")
                        .note("Customer pattern needs follow-up")
                        .createdAt(LocalDateTime.parse("2026-05-09T11:15:00"))
                        .build()))
                .build();
        when(riskCaseService.addNote(eq("case-1"), any(RiskCaseNoteRequest.class), eq("ops"))).thenReturn(response);

        mockMvc.perform(post("/api/risk/cases/case-1/notes")
                        .contentType("application/json")
                        .content("""
                                {
                                  "note": "Customer pattern needs follow-up"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes[0].noteId").value("note-1"))
                .andExpect(jsonPath("$.notes[0].author").value("ops"))
                .andExpect(jsonPath("$.notes[0].note").value("Customer pattern needs follow-up"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_ReturnsCaseCounts() throws Exception {
        when(riskCaseService.getSummary(any(), any())).thenReturn(new RiskCaseSummaryResponse(8, 4, 2, 1, 1, 3));

        mockMvc.perform(get("/api/risk/cases/summary")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-10T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCases").value(8))
                .andExpect(jsonPath("$.openCases").value(4))
                .andExpect(jsonPath("$.inReviewCases").value(2))
                .andExpect(jsonPath("$.resolvedCases").value(1))
                .andExpect(jsonPath("$.closedCases").value(1))
                .andExpect(jsonPath("$.unassignedCases").value(3));
    }
}
