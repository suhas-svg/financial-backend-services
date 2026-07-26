package com.suhasan.finance.account_service.config;

import com.suhasan.finance.account_service.service.DeploymentTrackingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class MonitoringConfigurationTest {
    @Test
    void deploymentHealthAndInfoCoverUpDownAndFailureEvidence() {
        HealthMonitoringConfig config = new HealthMonitoringConfig();
        DeploymentTrackingService deployments = mock(DeploymentTrackingService.class);
        var up = DeploymentTrackingService.DeploymentInfo.builder().version("v1").buildTime("now")
                .gitCommit("abc").environment("test").startTime(LocalDateTime.now())
                .lastDeploymentTime(LocalDateTime.now()).uptimeSeconds(10).healthScore(90).build();
        doReturn(up).when(deployments).getDeploymentInfo();
        assertThat(config.deploymentHealthIndicator(deployments).health().getStatus()).isEqualTo(Status.UP);

        var down = DeploymentTrackingService.DeploymentInfo.builder().healthScore(20).build();
        doReturn(down).when(deployments).getDeploymentInfo();
        assertThat(config.deploymentHealthIndicator(deployments).health().getStatus()).isEqualTo(Status.DOWN);

        doThrow(new IllegalStateException("unavailable")).when(deployments).getDeploymentInfo();
        assertThat(config.deploymentHealthIndicator(deployments).health().getDetails())
                .containsEntry("error", "unavailable");

        doReturn(up).when(deployments).getDeploymentInfo();
        Info.Builder successBuilder = new Info.Builder();
        config.deploymentInfoContributor(deployments).contribute(successBuilder);
        assertThat(successBuilder.build().getDetails()).containsKey("deployment");
        doThrow(new IllegalStateException("failed")).when(deployments).getDeploymentInfo();
        Info.Builder failureBuilder = new Info.Builder();
        config.deploymentInfoContributor(deployments).contribute(failureBuilder);
        @SuppressWarnings("unchecked")
        Map<String, Object> failedDeployment =
                (Map<String, Object>) failureBuilder.build().getDetails().get("deployment");
        assertThat(failedDeployment)
                .containsEntry("error", "failed");
    }

    @Test
    void databaseMemoryDiskAndSystemContributorsReturnStructuredEvidence() throws Exception {
        HealthMonitoringConfig config = new HealthMonitoringConfig();
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getDatabaseProductVersion()).thenReturn("17");
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost/db");
        when(connection.isValid(5)).thenReturn(true);
        assertThat(config.enhancedDatabaseHealthIndicator(dataSource).health().getStatus()).isEqualTo(Status.UP);

        when(dataSource.getConnection()).thenThrow(new SQLException("offline"));
        assertThat(config.enhancedDatabaseHealthIndicator(dataSource).health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(config.memoryHealthIndicator().health().getDetails()).containsKey("usage_percent");
        assertThat(config.diskSpaceHealthIndicator().health().getDetails()).containsKey("usage_percent");

        Info.Builder builder = new Info.Builder();
        config.systemInfoContributor().contribute(builder);
        @SuppressWarnings("unchecked")
        Map<String, Object> system = (Map<String, Object>) builder.build().getDetails().get("system");
        assertThat(system)
                .containsKeys("java_version", "os_name", "spring_profiles");
    }

    @Test
    void performanceFilterAndEveryMetricBeanTrackSuccessErrorsAndExceptions() throws Exception {
        PerformanceMonitoringConfig config = new PerformanceMonitoringConfig();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        var filter = config.performanceMonitoringFilter(meters);
        MockHttpServletResponse success = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), success, new MockFilterChain());
        MockHttpServletResponse failure = new MockHttpServletResponse();
        failure.setStatus(500);
        filter.doFilter(new MockHttpServletRequest(), failure, new MockFilterChain());
        assertThat(meters.get("http_requests_total").counter().count()).isEqualTo(2);
        assertThat(meters.get("http_errors_total").counter().count()).isEqualTo(1);

        assertThat(config.deploymentDurationTimer(meters)).isNotNull();
        assertThat(config.deploymentEventCounter(meters)).isNotNull();
        assertThat(config.deploymentTimeSinceLastGauge(meters).value()).isGreaterThanOrEqualTo(0);
        assertThat(config.applicationRequestRateGauge(meters).value()).isEqualTo(2);
        assertThat(config.applicationErrorRateGauge(meters).value()).isEqualTo(50);
        assertThat(config.postDeploymentHealthCheckCounter(meters)).isNotNull();
        assertThat(config.postDeploymentHealthCheckTimer(meters)).isNotNull();
        assertThat(config.deploymentRollbackCounter(meters)).isNotNull();
        assertThat(config.performanceRegressionScoreGauge(meters).value()).isEqualTo(50);
        config.updateDeploymentTime();
        config.resetCounters();
        assertThat(config.applicationErrorRateGauge(new SimpleMeterRegistry()).value()).isZero();
    }

    @Test
    void regressionScoreCoversPerfectLowAndModerateErrorBands() {
        PerformanceMonitoringConfig config = new PerformanceMonitoringConfig();
        AtomicLong requests = (AtomicLong) ReflectionTestUtils.getField(config, "requestCount");
        AtomicLong errors = (AtomicLong) ReflectionTestUtils.getField(config, "errorCount");
        Double perfect = ReflectionTestUtils.invokeMethod(config, "calculatePerformanceRegressionScore");
        assertThat(perfect).isEqualTo(100D);
        requests.set(1000);
        errors.set(5);
        Double low = ReflectionTestUtils.invokeMethod(config, "calculatePerformanceRegressionScore");
        assertThat(low).isEqualTo(90D);
        errors.set(20);
        Double moderate = ReflectionTestUtils.invokeMethod(config, "calculatePerformanceRegressionScore");
        assertThat(moderate).isEqualTo(70D);
    }
}
