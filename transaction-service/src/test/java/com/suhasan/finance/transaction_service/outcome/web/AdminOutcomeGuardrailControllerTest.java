package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailControlService;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailService;
import com.suhasan.finance.transaction_service.outcome.web.AdminOutcomeGuardrailController;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionDtos.GuardrailControlResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminOutcomeGuardrailController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import({com.suhasan.finance.transaction_service.security.SecurityConfig.class, JwtFilterTestConfig.class})
@EnableWebSecurity
class AdminOutcomeGuardrailControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean OutcomeGuardrailControlService controlService;
    @MockitoBean OutcomeGuardrailService guardrailService;

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void customerCannotReadOrChangeOperatorControl() throws Exception {
        mockMvc.perform(get("/api/admin/outcome-protection/guardrails/control"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/outcome-protection/guardrails/control")
                        .header("Idempotency-Key", "kill-1")
                        .contentType("application/json")
                        .content("{\"executionEnabled\":false,\"reason\":\"Emergency stop\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(controlService);
    }

    @Test
    @WithMockUser(username = "operator-1", roles = "ADMIN")
    void authorizedOperatorChangeCarriesIdentityReasonAndIdempotency() throws Exception {
        when(controlService.update(argThat(request -> !request.executionEnabled()
                        && request.reason().equals("Emergency stop")), eq("operator-1"), eq("kill-1")))
                .thenReturn(new GuardrailControlResponse(false, "Emergency stop", "operator-1", Instant.EPOCH));

        mockMvc.perform(put("/api/admin/outcome-protection/guardrails/control")
                        .header("Idempotency-Key", "kill-1")
                        .contentType("application/json")
                        .content("{\"executionEnabled\":false,\"reason\":\"Emergency stop\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionEnabled").value(false))
                .andExpect(jsonPath("$.changedBy").value("operator-1"));

        verify(controlService).update(argThat(request -> !request.executionEnabled()),
                eq("operator-1"), eq("kill-1"));
    }

    @Test
    @WithMockUser(username = "operator-1", roles = "ADMIN")
    void operatorMutationFailsClosedWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(put("/api/admin/outcome-protection/guardrails/control")
                        .contentType("application/json")
                        .content("{\"executionEnabled\":true,\"reason\":\"Approved window\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(controlService);
    }
}
