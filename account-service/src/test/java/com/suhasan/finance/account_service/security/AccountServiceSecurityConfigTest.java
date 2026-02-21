package com.suhasan.finance.account_service.security;

import com.suhasan.finance.account_service.controller.HealthController;
import com.suhasan.finance.account_service.service.DeploymentTrackingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AccountServiceSecurityConfigTest — validates the H2 security fix in the
 * account-service:
 *
 * <ul>
 * <li>GET /api/health/ping and GET /api/health/status remain publicly
 * accessible
 * (required for load balancer health probes)</li>
 * <li>POST /api/health/check and POST /api/health/deployment are restricted to
 * ADMIN or INTERNAL_SERVICE roles (H2 fix — previously public)</li>
 * <li>GET /api/health/deployment and GET /api/health/metrics require privileged
 * roles</li>
 * <li>Actuator probes are public; Prometheus/metrics require privileged
 * roles</li>
 * <li>/api/internal/** requires ADMIN or INTERNAL_SERVICE</li>
 * </ul>
 */
@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class, AccountServiceSecurityConfigTest.TestMeterRegistryConfig.class })
@DisplayName("Account-Service Security Configuration Tests (H2 fix)")
class AccountServiceSecurityConfigTest {

    @Configuration
    static class TestMeterRegistryConfig {
        @Bean
        public MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentTrackingService deploymentTrackingService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // ─────────────────────────────────────────────────────────────────────────
    // Public read-only probes (must remain accessible to load balancers)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Public health probes — no authentication required")
    class PublicProbes {

        @Test
        @DisplayName("GET /api/health/ping — unauthenticated → 200 (load balancer probe)")
        void ping_IsPublic() throws Exception {
            mockMvc.perform(get("/api/health/ping"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/health/status — unauthenticated → not 401")
        void status_IsPublic() throws Exception {
            mockMvc.perform(get("/api/health/status"))
                    .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus(),
                            "GET /api/health/status must be publicly accessible"));
        }

        @Test
        @DisplayName("GET /actuator/health — unauthenticated → not 401 (K8s liveness probe)")
        void actuatorHealth_IsPublic() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
        }

        @Test
        @DisplayName("GET /actuator/info — unauthenticated → not 401")
        void actuatorInfo_IsPublic() throws Exception {
            mockMvc.perform(get("/actuator/info"))
                    .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // H2 fix — privileged health write operations
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("H2 fix — health write endpoints require privileged role")
    class HealthWriteEndpointSecurity {

        @Test
        @DisplayName("POST /api/health/check — unauthenticated → 401")
        void healthCheck_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/health/check"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/health/deployment — unauthenticated → 401")
        void healthDeployment_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(post("/api/health/deployment").param("status", "success"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/health/check — regular USER → 403 Forbidden")
        @WithMockUser(username = "user", roles = "USER")
        void healthCheck_RegularUser_Returns403() throws Exception {
            mockMvc.perform(post("/api/health/check"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/health/deployment — regular USER → 403 Forbidden")
        @WithMockUser(username = "user", roles = "USER")
        void healthDeployment_RegularUser_Returns403() throws Exception {
            mockMvc.perform(post("/api/health/deployment").param("status", "success"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /api/health/check — ADMIN → passes security (200 or 503 is acceptable)")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void healthCheck_AdminRole_PassesSecurity() throws Exception {
            mockMvc.perform(post("/api/health/check"))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        assertNotEquals(401, s, "ADMIN must not receive 401");
                        assertNotEquals(403, s, "ADMIN must not receive 403");
                    });
        }

        @Test
        @DisplayName("POST /api/health/deployment — INTERNAL_SERVICE → passes security")
        @WithMockUser(username = "svc-account", roles = "INTERNAL_SERVICE")
        void healthDeployment_InternalService_PassesSecurity() throws Exception {
            mockMvc.perform(post("/api/health/deployment").param("status", "success"))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        assertNotEquals(401, s);
                        assertNotEquals(403, s);
                    });
        }

        @Test
        @DisplayName("GET /api/health/deployment — unauthenticated → 401")
        void getDeploymentInfo_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/health/deployment"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/health/metrics — unauthenticated → 401")
        void getMetrics_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/health/metrics"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/health/deployment — ADMIN → passes security")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void getDeploymentInfo_AdminRole_PassesSecurity() throws Exception {
            mockMvc.perform(get("/api/health/deployment"))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        assertNotEquals(401, s);
                        assertNotEquals(403, s);
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Privileged actuator endpoints
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Privileged actuator endpoints")
    class PrivilegedActuator {

        @Test
        @DisplayName("GET /actuator/prometheus — unauthenticated → 401")
        void prometheus_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /actuator/metrics — regular USER → 403")
        @WithMockUser(username = "user", roles = "USER")
        void actuatorMetrics_RegularUser_Returns403() throws Exception {
            mockMvc.perform(get("/actuator/metrics"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /actuator/prometheus — ADMIN → passes security")
        @WithMockUser(username = "admin", roles = "ADMIN")
        void prometheus_AdminRole_PassesSecurity() throws Exception {
            mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        assertNotEquals(401, s);
                        assertNotEquals(403, s);
                    });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal API
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Internal service API security")
    class InternalApiSecurity {

        @Test
        @DisplayName("GET /api/internal/accounts — unauthenticated → 401")
        void internalApi_Unauthenticated_Returns401() throws Exception {
            mockMvc.perform(get("/api/internal/accounts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/internal/accounts — regular USER → 403")
        @WithMockUser(username = "user", roles = "USER")
        void internalApi_RegularUser_Returns403() throws Exception {
            mockMvc.perform(get("/api/internal/accounts"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GET /api/internal/accounts — INTERNAL_SERVICE → passes security")
        @WithMockUser(username = "tx-service", roles = "INTERNAL_SERVICE")
        void internalApi_InternalService_PassesSecurity() throws Exception {
            mockMvc.perform(get("/api/internal/accounts"))
                    .andExpect(result -> {
                        int s = result.getResponse().getStatus();
                        assertNotEquals(401, s);
                        assertNotEquals(403, s);
                    });
        }
    }
}
