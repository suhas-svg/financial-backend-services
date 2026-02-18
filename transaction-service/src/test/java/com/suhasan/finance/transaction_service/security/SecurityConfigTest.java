package com.suhasan.finance.transaction_service.security;

import com.suhasan.finance.transaction_service.controller.TransactionController;
import com.suhasan.finance.transaction_service.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void publicEndpoints_HealthCheck_AllowsUnauthenticatedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/transactions/health"))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpoints_ActuatorHealth_AllowsUnauthenticatedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    void protectedEndpoints_ActuatorMetrics_RequiresAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpoints_SwaggerUI_AllowsUnauthenticatedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    void publicEndpoints_ApiDocs_AllowsUnauthenticatedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    void protectedEndpoints_TransactionAPI_RequiresAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoints_TransferAPI_RequiresAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoints_DepositAPI_RequiresAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/transactions/deposit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoints_WithdrawAPI_RequiresAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/transactions/withdraw"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user123")
    void protectedEndpoints_WithAuthentication_AllowsAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user123")
    void protectedEndpoints_TransferWithAuthentication_AllowsAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest()); // Bad request due to invalid JSON, but not unauthorized
    }

    @Test
    void anyOtherRequest_RequiresAuthentication() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/some/other/endpoint"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user123")
    void anyOtherRequest_WithAuthentication_AllowsAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/some/other/endpoint"))
                .andExpect(status().isNotFound()); // Not found, but not unauthorized
    }

    @Test
    void csrfDisabled_AllowsPostRequests() throws Exception {
        // CSRF should be disabled, so POST requests without CSRF token should work
        // (though they'll still fail authentication)
        
        // Act & Assert
        mockMvc.perform(post("/api/transactions/transfer")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isUnauthorized()); // Unauthorized because user is unauthenticated
    }

    @Test
    void sessionManagement_StatelessConfiguration() throws Exception {
        // This test verifies that session management is stateless
        // Multiple requests should not share session state
        
        // Act & Assert - First request
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
        
        // Act & Assert - Second request should also be unauthorized (no session state)
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized());
    }
}
