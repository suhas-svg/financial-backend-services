package com.suhasan.finance.account_service.controller;

import com.suhasan.finance.account_service.service.DeploymentTrackingService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthControllerTest {
    private DeploymentTrackingService deployments;
    private SimpleMeterRegistry meters;
    private HealthController controller;
    private AtomicInteger uptime;

    @BeforeEach
    void setUp() {
        deployments = mock(DeploymentTrackingService.class);
        meters = new SimpleMeterRegistry();
        meters.counter("deployment_total").increment(2);
        uptime = new AtomicInteger(9);
        Gauge.builder("application_uptime_seconds", uptime, AtomicInteger::doubleValue)
                .register(meters);
        controller = new HealthController(deployments, meters, emptyJdbcProvider());
    }

    /** No JdbcTemplate: the health checks must degrade to UNKNOWN rather than invent a status. */
    private static ObjectProvider<JdbcTemplate> emptyJdbcProvider() {
        return new ObjectProvider<JdbcTemplate>() {
            @Override
            public JdbcTemplate getObject(Object... args) {
                throw new NoSuchBeanDefinitionException(JdbcTemplate.class);
            }

            @Override
            public JdbcTemplate getObject() {
                throw new NoSuchBeanDefinitionException(JdbcTemplate.class);
            }

            @Override
            public JdbcTemplate getIfAvailable() {
                return null;
            }

            @Override
            public JdbcTemplate getIfUnique() {
                return null;
            }
        };
    }

    @Test
    void exposesPingHealthDeploymentAndMetricsSuccessPaths() {
        var info = DeploymentTrackingService.DeploymentInfo.builder().version("v1").build();
        when(deployments.performHealthCheck()).thenReturn(true);
        when(deployments.getDeploymentInfo()).thenReturn(info);

        assertThat(controller.ping().getBody()).isEqualTo("pong");
        assertThat(controller.getHealthStatus().getBody()).containsEntry("status", "UP")
                .containsEntry("deployment", info);
        assertThat(controller.getDeploymentInfo().getBody()).isSameAs(info);
        assertThat(controller.triggerHealthCheck().getBody()).containsEntry("healthy", true);
        assertThat(controller.getMetricsSummary().getBody())
                .containsEntry("deployment_total", 2D)
                .containsEntry("deployment_success_total", 0D)
                .containsEntry("application_uptime_seconds", 9D)
                .containsEntry("application_health_score", 0D);
    }

    @Test
    void reportsDownAndServiceUnavailableOnHealthFailures() {
        when(deployments.performHealthCheck()).thenReturn(false);
        when(deployments.getDeploymentInfo()).thenReturn(
                DeploymentTrackingService.DeploymentInfo.builder().build());
        assertThat(controller.getHealthStatus().getBody()).containsEntry("status", "DOWN");

        when(deployments.performHealthCheck()).thenThrow(new IllegalStateException("health failed"));
        assertThat(controller.getHealthStatus().getStatusCode().value()).isEqualTo(503);
        // /api/health/status is reachable without authentication, so the raw driver/exception
        // message must not be echoed back to the caller.
        assertThat(controller.getHealthStatus().getBody())
                .containsEntry("error", "Health status could not be determined")
                .doesNotContainValue("health failed");
        assertThat(controller.triggerHealthCheck().getStatusCode().value()).isEqualTo(503);
        assertThat(controller.triggerHealthCheck().getBody()).containsEntry("healthy", false);
    }

    @Test
    void handlesDeploymentInfoAndMetricsExceptions() {
        when(deployments.getDeploymentInfo()).thenThrow(new IllegalStateException("unavailable"));
        assertThat(controller.getDeploymentInfo().getStatusCode().value()).isEqualTo(500);

        var brokenMeters = mock(io.micrometer.core.instrument.MeterRegistry.class);
        when(brokenMeters.find("deployment_total")).thenThrow(new IllegalStateException("metrics unavailable"));
        assertThat(new HealthController(deployments, brokenMeters, emptyJdbcProvider())
                .getMetricsSummary().getStatusCode().value())
                .isEqualTo(500);
    }

    @Test
    void recordsSuccessFailureGenericAndDurationBranches() {
        assertThat(controller.recordDeployment("success", 12L).getBody())
                .containsEntry("message", "Deployment success recorded")
                .containsEntry("duration", "12ms");
        verify(deployments).recordDeploymentSuccess();
        verify(deployments).recordDeploymentDuration(12L);

        assertThat(controller.recordDeployment("failure", null).getBody())
                .containsEntry("message", "Deployment failure recorded");
        verify(deployments).recordDeploymentFailure("Deployment failed");

        assertThat(controller.recordDeployment("other", null).getBody())
                .containsEntry("message", "Deployment event recorded");
        verify(deployments).recordDeployment();
    }

    @Test
    void returnsServerErrorWhenDeploymentRecordingFails() {
        doThrow(new IllegalStateException("record failed")).when(deployments).recordDeployment();
        assertThat(controller.recordDeployment(null, null).getStatusCode().value()).isEqualTo(500);
        assertThat(controller.recordDeployment(null, null).getBody()).containsEntry("error", "record failed");
    }
}
