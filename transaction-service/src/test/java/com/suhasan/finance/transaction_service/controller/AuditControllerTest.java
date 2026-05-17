package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.AuditLogEntryResponse;
import com.suhasan.finance.transaction_service.dto.AuditSummaryResponse;
import com.suhasan.finance.transaction_service.service.AuditQueryService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuditController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import({com.suhasan.finance.transaction_service.security.SecurityConfig.class, JwtFilterTestConfig.class})
@EnableWebSecurity
class AuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditQueryService auditQueryService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listEvents_AllowsAdminAndPassesFilters() throws Exception {
        AuditLogEntryResponse entry = AuditLogEntryResponse.builder()
                .eventId("event-1")
                .eventType("TRANSACTION")
                .action("TRANSACTION_FAILED")
                .outcome("FAILURE")
                .userId("user-1")
                .transactionId("txn-1")
                .createdAt(LocalDateTime.parse("2026-05-08T10:15:30"))
                .build();
        Page<AuditLogEntryResponse> page = new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1);
        when(auditQueryService.searchEvents(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/audit/events")
                        .param("action", "TRANSACTION_FAILED")
                        .param("outcome", "FAILURE")
                        .param("userId", "user-1")
                        .param("transactionId", "txn-1")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-09T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value("event-1"))
                .andExpect(jsonPath("$.content[0].action").value("TRANSACTION_FAILED"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(auditQueryService).searchEvents(argThat(filter ->
                        "TRANSACTION_FAILED".equals(filter.getAction())
                                && "FAILURE".equals(filter.getOutcome())
                                && "user-1".equals(filter.getUserId())
                                && "txn-1".equals(filter.getTransactionId())
                                && LocalDateTime.parse("2026-05-01T00:00:00").equals(filter.getFrom())
                                && LocalDateTime.parse("2026-05-09T00:00:00").equals(filter.getTo())),
                any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listEvents_RejectsNormalUsers() throws Exception {
        mockMvc.perform(get("/api/audit/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSummary_ReturnsCounts() throws Exception {
        when(auditQueryService.getSummary(any(), any())).thenReturn(
                new AuditSummaryResponse(7, 2, 1, 1));

        mockMvc.perform(get("/api/audit/summary")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-09T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(7))
                .andExpect(jsonPath("$.failureEvents").value(2))
                .andExpect(jsonPath("$.reversalEvents").value(1))
                .andExpect(jsonPath("$.securityEvents").value(1));

        verify(auditQueryService).getSummary(
                eq(LocalDateTime.parse("2026-05-01T00:00:00")),
                eq(LocalDateTime.parse("2026-05-09T00:00:00")));
    }
}
