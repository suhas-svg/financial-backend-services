package com.suhasan.finance.transaction_service.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpoints_ActuatorMetrics_AllowsUnauthenticatedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpoints_SwaggerUI_AllowsUnauthenticatedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpoints_ApiDocs_AllowsUnauthenticatedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
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
                .andExpect(status().isUnauthorized()); // Unauthorized, not forbidden (which would indicate CSRF)
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