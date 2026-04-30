package com.suhasan.finance.account_service.security;

import com.suhasan.finance.account_service.controller.HealthController;
import com.suhasan.finance.account_service.service.DeploymentTrackingService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, AccountServiceSecurityConfigTest.TestMeterRegistryConfig.class })
@DisplayName("Account-Service Security Configuration Tests")
class AccountServiceSecurityConfigTest {

    @TestConfiguration
    static class TestMeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentTrackingService deploymentTrackingService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/health/ping is public")
    void ping_IsPublic() throws Exception {
        mockMvc.perform(get("/api/health/ping"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/health/status is public")
    void status_IsPublic() throws Exception {
        mockMvc.perform(get("/api/health/status"))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("GET /actuator/health is public")
    void actuatorHealth_IsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("GET /actuator/info is public")
    void actuatorInfo_IsPublic() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("POST /api/health/check unauthenticated returns 401")
    void healthCheck_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/health/check"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/health/deployment unauthenticated returns 401")
    void healthDeployment_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/health/deployment").param("status", "success"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("POST /api/health/check regular user returns 403")
    void healthCheck_RegularUser_Returns403() throws Exception {
        mockMvc.perform(post("/api/health/check"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("POST /api/health/deployment regular user returns 403")
    void healthDeployment_RegularUser_Returns403() throws Exception {
        mockMvc.perform(post("/api/health/deployment").param("status", "success"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("POST /api/health/check admin passes security")
    void healthCheck_AdminRole_PassesSecurity() throws Exception {
        mockMvc.perform(post("/api/health/check"))
                .andExpect(result -> assertNotUnauthorizedOrForbidden(result.getResponse().getStatus()));
    }

    @Test
    @WithMockUser(username = "svc-account", roles = "INTERNAL_SERVICE")
    @DisplayName("POST /api/health/deployment internal service passes security")
    void healthDeployment_InternalService_PassesSecurity() throws Exception {
        mockMvc.perform(post("/api/health/deployment").param("status", "success"))
                .andExpect(result -> assertNotUnauthorizedOrForbidden(result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("GET /api/health/deployment unauthenticated returns 401")
    void getDeploymentInfo_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/health/deployment"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/health/metrics unauthenticated returns 401")
    void getMetrics_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/health/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("GET /api/health/deployment admin passes security")
    void getDeploymentInfo_AdminRole_PassesSecurity() throws Exception {
        mockMvc.perform(get("/api/health/deployment"))
                .andExpect(result -> assertNotUnauthorizedOrForbidden(result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("GET /actuator/prometheus unauthenticated returns 401")
    void prometheus_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("GET /actuator/metrics regular user returns 403")
    void actuatorMetrics_RegularUser_Returns403() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("GET /actuator/prometheus admin passes security")
    void prometheus_AdminRole_PassesSecurity() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(result -> assertNotUnauthorizedOrForbidden(result.getResponse().getStatus()));
    }

    @Test
    @DisplayName("GET /api/internal/accounts unauthenticated returns 401")
    void internalApi_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/internal/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    @DisplayName("GET /api/internal/accounts regular user returns 403")
    void internalApi_RegularUser_Returns403() throws Exception {
        mockMvc.perform(get("/api/internal/accounts"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "tx-service", roles = "INTERNAL_SERVICE")
    @DisplayName("GET /api/internal/accounts internal service passes security")
    void internalApi_InternalService_PassesSecurity() throws Exception {
        mockMvc.perform(get("/api/internal/accounts"))
                .andExpect(result -> assertNotUnauthorizedOrForbidden(result.getResponse().getStatus()));
    }

    private void assertNotUnauthorizedOrForbidden(int status) {
        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }
}
