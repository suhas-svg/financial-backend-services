package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.dto.RiskAlertResponse;
import com.suhasan.finance.transaction_service.dto.RiskSummaryResponse;
import com.suhasan.finance.transaction_service.entity.RiskAlertSeverity;
import com.suhasan.finance.transaction_service.entity.RiskAlertStatus;
import com.suhasan.finance.transaction_service.entity.RiskAlertType;
import com.suhasan.finance.transaction_service.service.RiskAlertQueryService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = RiskAlertController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import({com.suhasan.finance.transaction_service.security.SecurityConfig.class, JwtFilterTestConfig.class})
@EnableWebSecurity
class RiskAlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiskAlertQueryService riskAlertQueryService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void listAlerts_AllowsAdminAndPassesFilters() throws Exception {
        RiskAlertResponse alert = RiskAlertResponse.builder()
                .alertId("alert-1")
                .alertType(RiskAlertType.HIGH_VALUE_TRANSFER)
                .severity(RiskAlertSeverity.HIGH)
                .status(RiskAlertStatus.OPEN)
                .userId("user-1")
                .transactionId("txn-1")
                .amount(new BigDecimal("6000.00"))
                .currency("USD")
                .reason("Transfer amount exceeded high-value threshold")
                .createdAt(LocalDateTime.parse("2026-05-09T10:15:30"))
                .build();
        Page<RiskAlertResponse> page = new PageImpl<>(List.of(alert), PageRequest.of(0, 20), 1);
        when(riskAlertQueryService.searchAlerts(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/risk/alerts")
                        .param("status", "OPEN")
                        .param("severity", "HIGH")
                        .param("alertType", "HIGH_VALUE_TRANSFER")
                        .param("userId", "user-1")
                        .param("transactionId", "txn-1")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-10T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].alertId").value("alert-1"))
                .andExpect(jsonPath("$.content[0].alertType").value("HIGH_VALUE_TRANSFER"))
                .andExpect(jsonPath("$.content[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(riskAlertQueryService).searchAlerts(argThat(filter ->
                        filter.getStatus() == RiskAlertStatus.OPEN
                                && filter.getSeverity() == RiskAlertSeverity.HIGH
                                && filter.getAlertType() == RiskAlertType.HIGH_VALUE_TRANSFER
                                && "user-1".equals(filter.getUserId())
                                && "txn-1".equals(filter.getTransactionId())
                                && LocalDateTime.parse("2026-05-01T00:00:00").equals(filter.getFrom())
                                && LocalDateTime.parse("2026-05-10T00:00:00").equals(filter.getTo())),
                any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listAlerts_RejectsNormalUsers() throws Exception {
        mockMvc.perform(get("/api/risk/alerts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSummary_ReturnsCounts() throws Exception {
        when(riskAlertQueryService.getSummary(any(), any())).thenReturn(
                new RiskSummaryResponse(12, 8, 3, 2));

        mockMvc.perform(get("/api/risk/summary")
                        .param("from", "2026-05-01T00:00:00")
                        .param("to", "2026-05-10T00:00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAlerts").value(12))
                .andExpect(jsonPath("$.openAlerts").value(8))
                .andExpect(jsonPath("$.highSeverityAlerts").value(3))
                .andExpect(jsonPath("$.escalatedAlerts").value(2));

        verify(riskAlertQueryService).getSummary(
                eq(LocalDateTime.parse("2026-05-01T00:00:00")),
                eq(LocalDateTime.parse("2026-05-10T00:00:00")));
    }

    @Test
    @WithMockUser(username = "admin-1", roles = "ADMIN")
    void updateStatus_PersistsReviewerAndNote() throws Exception {
        RiskAlertResponse response = RiskAlertResponse.builder()
                .alertId("alert-1")
                .status(RiskAlertStatus.ESCALATED)
                .reviewedBy("admin-1")
                .resolutionNote("Escalated to fraud operations")
                .build();
        when(riskAlertQueryService.updateStatus(eq("alert-1"), any(), eq("admin-1"))).thenReturn(response);

        mockMvc.perform(patch("/api/risk/alerts/alert-1/status")
                        .contentType("application/json")
                        .content("""
                                {
                                  "status": "ESCALATED",
                                  "resolutionNote": "Escalated to fraud operations"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ESCALATED"))
                .andExpect(jsonPath("$.reviewedBy").value("admin-1"))
                .andExpect(jsonPath("$.resolutionNote").value("Escalated to fraud operations"));

        verify(riskAlertQueryService).updateStatus(eq("alert-1"), argThat(request ->
                request.getStatus() == RiskAlertStatus.ESCALATED
                        && "Escalated to fraud operations".equals(request.getResolutionNote())), eq("admin-1"));
    }
}
