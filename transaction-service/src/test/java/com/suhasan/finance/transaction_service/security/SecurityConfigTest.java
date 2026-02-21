package com.suhasan.finance.transaction_service.security;

import com.suhasan.finance.transaction_service.controller.TransactionController;
import com.suhasan.finance.transaction_service.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

/**
 * SecurityConfigTest — validates the security rules introduced/updated by the
 * H1 fix (monitoring endpoint restriction) and the general security hardening.
 *
 * <p>
 * Coverage:
 * <ul>
 * <li>Public health probe endpoints remain accessible without
 * authentication</li>
 * <li>/api/monitoring/** requires ADMIN or INTERNAL_SERVICE role (H1 fix)</li>
 * <li>Ordinary authenticated users are FORBIDDEN on monitoring endpoints</li>
 * <li>/api/transactions/** requires authentication</li>
 * <li>Actuator /health is public; /metrics and /prometheus require privileged
 * roles</li>
 * <li>Stateless session — no session state leaks between requests</li>
 * </ul>
 */
@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
@DisplayName("Transaction-Service Security Configuration Tests")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    // ─────────────────────────────────────────────────────────────────────────
    // Public endpoints
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Public endpoints — no authentication required")
    class PublicEndpoints {

        @Test
        @DisplayName("GET /api/transactions/health — accessible unauthenticated")
        void healthEndpoint_IsPublic() throws Exception {
            mockMvc.perform(get("/api/transactions/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /actuator/health — accessible unauthenticated (K8s liveness probe)")
        void actuatorHealth_IsPublic() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(401,
                            result.getResponse().getStatus(),
                            "/actuator/health must not return 401"));
        }

        @Test
        @DisplayName("GET /actuator/info — accessible unauthenticated")
        void actuatorInfo_IsPublic() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(401,
                            result.getResponse().getStatus()));
        }

        @Test
        @DisplayName("GET /swagger-ui/index.html — accessible unauthenticated")
        void swaggerUi_IsPublic() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(401,
                            result.getResponse().getStatus()));
        }

        @Test
        @DisplayName("GET /v3/api-docs — accessible unauthenticated")
        void openApiDocs_IsPublic() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(result -> org.junit.jupiter.api.Assertions.assertNotEquals(401,
                            result.getResponse().getStatus()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // H1 fix — monitoring endpoints restricted to ADMIN/INTERNAL_SERVICE
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("H1 fix — /api/monitoring/** requires privileged role")
    class MonitoringEndpointSecurity {

        @Test
        @DisplayName("Unauthenticated request to /api/monitoring/** → 401")
        void monitoring_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/monitoring/circuit-breakers"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Authenticated USER (no admin role) on /api/monitoring/** → 403")
        @WithMockUser(username = "regularuser", roles = "USER")
        void monitoring_RegularUser_Returns403() throws Exception {
            mockMvc.perform(get("/api/monitoring/circuit-breakers"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN role on /api/monitoring/** → passes security (may 404 if no handler)")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void monitoring_AdminRole_PassesSecurity() throws Exception {
            mockMvc.perform(get("/api/monitoring/circuit-breakers"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // Security passes → NOT 401 or 403
                        org.junit.jupiter.api.Assertions.assertNotEquals(401, status,
                                "ADMIN should pass the security filter");
                        org.junit.jupiter.api.Assertions.assertNotEquals(403, status,
                                "ADMIN should not be forbidden");
                    });
        }

        @Test
        @DisplayName("INTERNAL_SERVICE role on /api/monitoring/** → passes security")
        @WithMockUser(username = "service-account", roles = "INTERNAL_SERVICE")
        void monitoring_InternalServiceRole_PassesSecurity() throws Exception {
            mockMvc.perform(get("/api/monitoring/health-summary"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.junit.jupiter.api.Assertions.assertNotEquals(401, status);
                        org.junit.jupiter.api.Assertions.assertNotEquals(403, status);
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Transaction endpoints — require authentication
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Transaction endpoints — authentication required")
    class TransactionEndpointSecurity {

        @Test
        @DisplayName("GET /api/transactions — unauthenticated → 401")
        void transactions_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/transactions"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/transactions/transfer — unauthenticated → 401")
        void transfer_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/transactions/transfer")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/transactions/deposit — unauthenticated → 401")
        void deposit_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/transactions/deposit")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/transactions/withdraw — unauthenticated → 401")
        void withdraw_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/transactions/withdraw")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/transactions — authenticated USER → 200 OK")
        @WithMockUser(username = "user123", roles = "USER")
        void transactions_Authenticated_Returns200() throws Exception {
            mockMvc.perform(get("/api/transactions"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /api/transactions/transfer — authenticated, bad body → 400 not 401")
        @WithMockUser(username = "user123", roles = "USER")
        void transfer_AuthenticatedBadBody_Returns400NotUnauthorized() throws Exception {
            mockMvc.perform(post("/api/transactions/transfer")
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Actuator endpoint security
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Actuator endpoint security")
    class ActuatorSecurity {

        @Test
        @DisplayName("GET /actuator/metrics — unauthenticated → 401")
        void actuatorMetrics_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/actuator/metrics"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /actuator/prometheus — unauthenticated → 401")
        void actuatorPrometheus_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /actuator/metrics — regular USER → 403 Forbidden")
        @WithMockUser(username = "user", roles = "USER")
        void actuatorMetrics_RegularUser_Returns403() throws Exception {
            mockMvc.perform(get("/actuator/metrics"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /actuator/prometheus — ADMIN → passes security")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void actuatorPrometheus_Admin_PassesSecurity() throws Exception {
            mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        org.junit.jupiter.api.Assertions.assertNotEquals(401, status);
                        org.junit.jupiter.api.Assertions.assertNotEquals(403, status);
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stateless session
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Stateless — second unauthenticated request is also rejected (no session reuse)")
    void statelessSession_NoSessionStateLeaks() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF disabled — POST without CSRF token is processed (fails auth, not CSRF)")
    void csrfDisabled_PostWithoutTokenFailsAuthNotCsrf() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer")
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized()); // 401, not 403 CSRF
    }
}
