package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.InvestigationFilter;
import com.suhasan.finance.transaction_service.dto.InvestigationSummaryResponse;
import com.suhasan.finance.transaction_service.dto.InvestigationTimelineItemResponse;
import com.suhasan.finance.transaction_service.security.JwtAuthenticationFilter;
import com.suhasan.finance.transaction_service.service.InvestigationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = InvestigationController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import({com.suhasan.finance.transaction_service.security.SecurityConfig.class, InvestigationControllerTest.JwtFilterTestConfig.class})
@EnableWebSecurity
class InvestigationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestigationService investigationService;

    @TestConfiguration
    static class JwtFilterTestConfig {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void timeline_AllowsAdminAndPassesSearchFilters() throws Exception {
        InvestigationTimelineItemResponse item = InvestigationTimelineItemResponse.builder()
                .itemId("txn-1")
                .itemType("TRANSACTION")
                .title("TRANSFER COMPLETED")
                .description("High value transfer")
                .status("COMPLETED")
                .userId("customer")
                .transactionId("txn-1")
                .accountId("101")
                .amount("6000.00")
                .currency("USD")
                .createdAt(LocalDateTime.parse("2026-05-10T10:15:30"))
                .metadata(Map.of("type", "TRANSFER"))
                .build();
        Page<InvestigationTimelineItemResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 50), 1);
        when(investigationService.getTimeline(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/investigations/timeline")
                        .param("userId", "customer")
                        .param("transactionId", "txn-1")
                        .param("accountId", "101")
                        .param("alertId", "alert-1")
                        .param("caseId", "case-1")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-11T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemType").value("TRANSACTION"))
                .andExpect(jsonPath("$.content[0].transactionId").value("txn-1"))
                .andExpect(jsonPath("$.content[0].accountId").value("101"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(investigationService).getTimeline(argThat(filter ->
                        "customer".equals(filter.getUserId())
                                && "txn-1".equals(filter.getTransactionId())
                                && "101".equals(filter.getAccountId())
                                && "alert-1".equals(filter.getAlertId())
                                && "case-1".equals(filter.getCaseId())
                                && LocalDateTime.parse("2026-05-01T00:00:00").equals(filter.getFrom())
                                && LocalDateTime.parse("2026-05-11T00:00:00").equals(filter.getTo())),
                any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void timeline_RejectsNormalUsers() throws Exception {
        mockMvc.perform(get("/api/investigations/timeline"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void export_AllowsAdminAndReturnsCsvAttachment() throws Exception {
        when(investigationService.exportTimelineCsv(any())).thenReturn(
                "itemId,itemType,title,description,severity,status,userId,transactionId,accountId,alertId,caseId,amount,currency,createdAt,metadataJson\n" +
                        "txn-1,TRANSACTION,TRANSFER COMPLETED,High value transfer,,COMPLETED,customer,txn-1,101,,,6000.00,USD,2026-05-10T10:15:30,{}\n");

        mockMvc.perform(get("/api/investigations/export")
                        .param("caseId", "case-1")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-11T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"investigation-export.csv\""))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
                .andExpect(content().string(containsString("txn-1,TRANSACTION")));

        verify(investigationService).exportTimelineCsv(argThat(filter ->
                "case-1".equals(filter.getCaseId())
                        && LocalDateTime.parse("2026-05-01T00:00:00").equals(filter.getFrom())
                        && LocalDateTime.parse("2026-05-11T00:00:00").equals(filter.getTo())));
    }

    @Test
    @WithMockUser(roles = "USER")
    void export_RejectsNormalUsers() throws Exception {
        mockMvc.perform(get("/api/investigations/export")
                        .param("caseId", "case-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_ReturnsInvestigationCounts() throws Exception {
        when(investigationService.getSummary(any())).thenReturn(new InvestigationSummaryResponse(2, 3, 1, 1, 2, 1, 1));

        mockMvc.perform(get("/api/investigations/summary")
                        .param("caseId", "case-1")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-11T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions").value(2))
                .andExpect(jsonPath("$.auditEvents").value(3))
                .andExpect(jsonPath("$.riskAlerts").value(1))
                .andExpect(jsonPath("$.riskCases").value(1))
                .andExpect(jsonPath("$.failures").value(2))
                .andExpect(jsonPath("$.reversals").value(1))
                .andExpect(jsonPath("$.highSeverityItems").value(1));

        verify(investigationService).getSummary(argThat(filter ->
                "case-1".equals(filter.getCaseId())
                        && LocalDateTime.parse("2026-05-01T00:00:00").equals(filter.getFrom())
                        && LocalDateTime.parse("2026-05-11T00:00:00").equals(filter.getTo())));
    }
}
