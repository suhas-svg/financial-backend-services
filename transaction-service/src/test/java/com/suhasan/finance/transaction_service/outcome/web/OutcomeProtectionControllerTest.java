package com.suhasan.finance.transaction_service.controller;

import com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailControlService;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeGuardrailService;
import com.suhasan.finance.transaction_service.outcome.service.OutcomeProtectionService;
import com.suhasan.finance.transaction_service.outcome.web.OutcomeProtectionController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OutcomeProtectionController.class,
        properties = "security.jwt.secret=01234567890123456789012345678901")
@Import({com.suhasan.finance.transaction_service.security.SecurityConfig.class, JwtFilterTestConfig.class})
@EnableWebSecurity
class OutcomeProtectionControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean OutcomeProtectionService service;
    @MockitoBean OutcomeGuardrailService guardrailService;
    @MockitoBean OutcomeGuardrailControlService guardrailControlService;

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void createUsesAuthenticatedCustomerAndIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/outcome-protection/scenarios")
                        .header("Idempotency-Key", "test")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Rent buffer",
                                  "accountIds":["101"],
                                  "currency":"INR",
                                  "timeZone":"Asia/Calcutta",
                                  "horizonStart":"2026-07-15",
                                  "horizonDays":30,
                                  "protectedMinimum":10000.00,
                                  "assumptions":[{
                                    "id":"rent","date":"2026-07-20","amount":-15000.00,
                                    "type":"EXPENSE","label":"Rent","flexible":false,"critical":true
                                  }],
                                  "shocks":[{
                                    "id":"rent-spike","type":"EXPENSE_SPIKE","targetAssumptionId":"rent",
                                    "amount":1000.00,"label":"Rent is higher"
                                  }]
                                }
                                """))
                .andExpect(status().isCreated());

        verify(service).create(argThat(request -> request.horizonDays() == 30
                        && request.protectedMinimum().compareTo(new java.math.BigDecimal("10000.00")) == 0
                        && request.accountIds().equals(java.util.List.of("101"))),
                org.mockito.ArgumentMatchers.eq("customer"), org.mockito.ArgumentMatchers.eq("test"));
    }

    @Test
    @WithMockUser(username = "customer", roles = "USER")
    void guardrailAcceptanceRequiresExplicitConfirmationPayload() throws Exception {
        mockMvc.perform(post("/api/outcome-protection/guardrails/guardrail-1/accept")
                        .header("Idempotency-Key", "test")
                        .contentType("application/json")
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk());

        verify(service).acceptGuardrail(org.mockito.ArgumentMatchers.eq("guardrail-1"),
                argThat(request -> request.confirmed()), org.mockito.ArgumentMatchers.eq("customer"),
                org.mockito.ArgumentMatchers.eq("test"));
    }

    @Test
    void unauthenticatedScenarioMutationIsRejected() throws Exception {
        mockMvc.perform(post("/api/outcome-protection/scenarios")
                        .header("Idempotency-Key", "test")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
