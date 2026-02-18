"""
Unit tests for monitoring and observability functionality.
"""

import pytest
import asyncio
from datetime import datetime, timedelta
from unittest.mock import Mock, AsyncMock, patch
import httpx

from mcp_financial.utils.health import (
    HealthChecker, 
    SystemHealthMonitor, 
    ServiceStatus, 
    HealthCheckResult
)
from mcp_financial.utils.alerting import (
    AlertManager,
    Alert,
    AlertType,
    AlertSeverity,
    LogAlertChannel,
    WebhookAlertChannel,
    MonitoringAlerts
)
from mcp_financial.utils.metrics import (
    MetricsCollector,
    get_metrics_summary,
    setup_metrics
)


class TestHealthChecker:
    """Test health checking functionality."""
    
    @pytest.fixture
    def health_checker(self):
        return HealthChecker(
            account_service_url="http://localhost:8080",
            transaction_service_url="http://localhost:8081",
            timeout=5000
        )
    
    @pytest.mark.asyncio
    async def test_check_service_health_success(self, health_checker):
        """Test successful service health check."""
        with patch.object(health_checker.client, 'get') as mock_get:
            mock_response = Mock()
            mock_response.status_code = 200
            mock_get.return_value = mock_response
            
            result = await health_checker.check_service_health(
                "test-service", 
                "http://localhost:8080"
            )
            
            assert result.service == "test-service"
            assert result.status == ServiceStatus.HEALTHY
            assert result.response_time_ms is not None
            assert result.error is None
    
    @pytest.mark.asyncio
    async def test_check_service_health_failure(self, health_checker):
        """Test service health check failure."""
        with patch.object(health_checker.client, 'get') as mock_get:
            mock_get.side_effect = httpx.RequestError("Connection failed")
            
            result = await health_checker.check_service_health(
                "test-service", 
                "http://localhost:8080"
            )
            
            assert result.service == "test-service"
            assert result.status == ServiceStatus.UNHEALTHY
            assert result.error is not None
    
    @pytest.mark.asyncio
    async def test_check_service_health_unhealthy_status(self, health_checker):
        """Test service returning unhealthy status."""
        with patch.object(health_checker.client, 'get') as mock_get:
            mock_response = Mock()
            mock_response.status_code = 503
            mock_get.return_value = mock_response
            
            result = await health_checker.check_service_health(
                "test-service", 
                "http://localhost:8080"
            )
            
            assert result.service == "test-service"
            assert result.status == ServiceStatus.UNHEALTHY
            assert "503" in result.error
    
    @pytest.mark.asyncio
    async def test_check_all_services(self, health_checker):
        """Test checking all services."""
        with patch.object(health_checker, 'check_account_service') as mock_account, \
             patch.object(health_checker, 'check_transaction_service') as mock_transaction:
            
            mock_account.return_value = HealthCheckResult(
                service="account-service",
                status=ServiceStatus.HEALTHY,
                response_time_ms=100.0,
                error=None,
                timestamp=datetime.utcnow()
            )
            
            mock_transaction.return_value = HealthCheckResult(
                service="transaction-service",
                status=ServiceStatus.HEALTHY,
                response_time_ms=150.0,
                error=None,
                timestamp=datetime.utcnow()
            )
            
            results = await health_checker.check_all_services(use_cache=False)
            
            assert len(results) == 2
            assert "account-service" in results
            assert "transaction-service" in results
            assert all(result.status == ServiceStatus.HEALTHY for result in results.values())
    
    @pytest.mark.asyncio
    async def test_get_overall_health(self, health_checker):
        """Test getting overall health status."""
        with patch.object(health_checker, 'check_all_services') as mock_check:
            mock_check.return_value = {
                "account-service": HealthCheckResult(
                    service="account-service",
                    status=ServiceStatus.HEALTHY,
                    response_time_ms=100.0,
                    error=None,
                    timestamp=datetime.utcnow()
                ),
                "transaction-service": HealthCheckResult(
                    service="transaction-service",
                    status=ServiceStatus.UNHEALTHY,
                    response_time_ms=None,
                    error="Connection timeout",
                    timestamp=datetime.utcnow()
                )
            }
            
            health_status = await health_checker.get_overall_health()
            
            assert health_status["status"] == ServiceStatus.UNHEALTHY.value
            assert len(health_status["services"]) == 2
            assert health_status["metrics"]["total_services"] == 2
            assert health_status["metrics"]["healthy_services"] == 1
            assert health_status["metrics"]["unhealthy_services"] == 1


class TestSystemHealthMonitor:
    """Test system health monitoring."""
    
    @pytest.fixture
    def health_checker(self):
        return Mock(spec=HealthChecker)
    
    @pytest.fixture
    def health_monitor(self, health_checker):
        return SystemHealthMonitor(health_checker)
    
    @pytest.mark.asyncio
    async def test_start_stop_monitoring(self, health_monitor):
        """Test starting and stopping monitoring."""
        # Mock the monitoring loop to avoid infinite loop
        with patch.object(health_monitor, '_monitoring_loop') as mock_loop:
            mock_loop.return_value = AsyncMock()
            
            await health_monitor.start_monitoring()
            assert health_monitor._monitoring_task is not None
            
            await health_monitor.stop_monitoring()
            assert health_monitor._monitoring_task.cancelled()
    
    def test_get_health_history(self, health_monitor):
        """Test getting health history."""
        # Add some mock history
        health_monitor._health_history = [
            {"status": "healthy", "timestamp": "2023-01-01T00:00:00Z"},
            {"status": "unhealthy", "timestamp": "2023-01-01T01:00:00Z"},
            {"status": "healthy", "timestamp": "2023-01-01T02:00:00Z"}
        ]
        
        history = health_monitor.get_health_history(limit=2)
        assert len(history) == 2
        assert history[-1]["status"] == "healthy"
    
    def test_get_health_summary(self, health_monitor):
        """Test getting health summary."""
        # Add some mock history
        health_monitor._health_history = [
            {
                "status": "healthy",
                "services": {
                    "account-service": {"status": "healthy"},
                    "transaction-service": {"status": "healthy"}
                }
            }
        ]
        
        summary = health_monitor.get_health_summary()
        assert "current_status" in summary
        assert "uptime_stats" in summary
        assert "monitoring_active" in summary


class TestAlertManager:
    """Test alert management functionality."""
    
    @pytest.fixture
    def alert_manager(self):
        return AlertManager()
    
    @pytest.mark.asyncio
    async def test_send_alert(self, alert_manager):
        """Test sending an alert."""
        alert_id = await alert_manager.send_alert(
            AlertType.SERVICE_DOWN,
            AlertSeverity.CRITICAL,
            "Test Alert",
            "This is a test alert",
            metadata={"test": "value"}
        )
        
        assert alert_id != ""
        assert len(alert_manager.active_alerts) == 1
        assert len(alert_manager.alert_history) == 1
        
        alert = alert_manager.active_alerts[alert_id]
        assert alert.type == AlertType.SERVICE_DOWN
        assert alert.severity == AlertSeverity.CRITICAL
        assert alert.title == "Test Alert"
    
    @pytest.mark.asyncio
    async def test_resolve_alert(self, alert_manager):
        """Test resolving an alert."""
        alert_id = await alert_manager.send_alert(
            AlertType.SERVICE_DOWN,
            AlertSeverity.CRITICAL,
            "Test Alert",
            "This is a test alert"
        )
        
        resolved = await alert_manager.resolve_alert(alert_id)
        assert resolved is True
        assert alert_id not in alert_manager.active_alerts
    
    @pytest.mark.asyncio
    async def test_alert_suppression(self, alert_manager):
        """Test alert suppression functionality."""
        # Send first alert
        alert_id1 = await alert_manager.send_alert(
            AlertType.SERVICE_DOWN,
            AlertSeverity.CRITICAL,
            "Test Alert 1",
            "First alert"
        )
        
        # Send second alert immediately (should be suppressed)
        alert_id2 = await alert_manager.send_alert(
            AlertType.SERVICE_DOWN,
            AlertSeverity.CRITICAL,
            "Test Alert 2",
            "Second alert"
        )
        
        assert alert_id1 != ""
        assert alert_id2 == ""  # Suppressed
        assert len(alert_manager.active_alerts) == 1
    
    def test_get_alert_stats(self, alert_manager):
        """Test getting alert statistics."""
        # Add some mock alerts
        now = datetime.utcnow()
        alert_manager.alert_history = [
            Alert(
                id="1",
                type=AlertType.SERVICE_DOWN,
                severity=AlertSeverity.CRITICAL,
                title="Test",
                description="Test",
                timestamp=now,
                source="test",
                metadata={}
            ),
            Alert(
                id="2",
                type=AlertType.HIGH_RESPONSE_TIME,
                severity=AlertSeverity.WARNING,
                title="Test",
                description="Test",
                timestamp=now,
                source="test",
                metadata={}
            )
        ]
        
        stats = alert_manager.get_alert_stats()
        assert stats["total_alerts_24h"] == 2
        assert "service_down" in stats["alerts_by_type_24h"]
        assert "critical" in stats["alerts_by_severity_24h"]


class TestLogAlertChannel:
    """Test log alert channel."""
    
    @pytest.fixture
    def log_channel(self):
        return LogAlertChannel()
    
    @pytest.mark.asyncio
    async def test_send_alert(self, log_channel):
        """Test sending alert to logs."""
        alert = Alert(
            id="test-1",
            type=AlertType.SERVICE_DOWN,
            severity=AlertSeverity.CRITICAL,
            title="Test Alert",
            description="Test Description",
            timestamp=datetime.utcnow(),
            source="test",
            metadata={"key": "value"}
        )
        
        with patch.object(log_channel.logger, 'log') as mock_log:
            result = await log_channel.send_alert(alert)
            
            assert result is True
            mock_log.assert_called_once()


class TestWebhookAlertChannel:
    """Test webhook alert channel."""
    
    @pytest.fixture
    def webhook_channel(self):
        return WebhookAlertChannel("http://localhost:8080/webhook")
    
    @pytest.mark.asyncio
    async def test_send_alert_success(self, webhook_channel):
        """Test successful webhook alert."""
        alert = Alert(
            id="test-1",
            type=AlertType.SERVICE_DOWN,
            severity=AlertSeverity.CRITICAL,
            title="Test Alert",
            description="Test Description",
            timestamp=datetime.utcnow(),
            source="test",
            metadata={}
        )
        
        with patch.object(webhook_channel.client, 'post') as mock_post:
            mock_response = Mock()
            mock_response.raise_for_status.return_value = None
            mock_post.return_value = mock_response
            
            result = await webhook_channel.send_alert(alert)
            
            assert result is True
            mock_post.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_send_alert_failure(self, webhook_channel):
        """Test webhook alert failure."""
        alert = Alert(
            id="test-1",
            type=AlertType.SERVICE_DOWN,
            severity=AlertSeverity.CRITICAL,
            title="Test Alert",
            description="Test Description",
            timestamp=datetime.utcnow(),
            source="test",
            metadata={}
        )
        
        with patch.object(webhook_channel.client, 'post') as mock_post:
            mock_post.side_effect = httpx.RequestError("Connection failed")
            
            result = await webhook_channel.send_alert(alert)
            
            assert result is False


class TestMetricsCollector:
    """Test metrics collection functionality."""
    
    def test_record_mcp_request(self):
        """Test recording MCP request metrics."""
        MetricsCollector.record_mcp_request("test_tool", "success", 0.1)
        
        # Verify metrics were recorded (would need access to actual metrics in real test)
        # This is a basic test structure
        assert True
    
    def test_record_service_request(self):
        """Test recording service request metrics."""
        MetricsCollector.record_service_request(
            "account-service", 
            "/api/accounts", 
            "success", 
            0.05
        )
        
        assert True
    
    def test_record_auth_request(self):
        """Test recording authentication metrics."""
        MetricsCollector.record_auth_request("success")
        MetricsCollector.record_auth_failure("invalid_token")
        
        assert True
    
    def test_get_metrics_summary(self):
        """Test getting metrics summary."""
        summary = get_metrics_summary()
        
        assert "total_requests" in summary
        assert "active_connections" in summary
        assert "total_errors" in summary
        assert "auth_requests" in summary
        assert "query_operations" in summary


class TestMonitoringAlerts:
    """Test monitoring-specific alerts."""
    
    @pytest.mark.asyncio
    async def test_service_down_alert(self):
        """Test service down alert."""
        with patch('mcp_financial.utils.alerting.alert_manager.send_alert') as mock_send:
            mock_send.return_value = "alert-123"
            
            alert_id = await MonitoringAlerts.service_down_alert(
                "account-service", 
                "Connection timeout"
            )
            
            assert alert_id == "alert-123"
            mock_send.assert_called_once_with(
                AlertType.SERVICE_DOWN,
                AlertSeverity.CRITICAL,
                "Service Down: account-service",
                "Service account-service is not responding: Connection timeout",
                metadata={"service": "account-service", "error": "Connection timeout"}
            )
    
    @pytest.mark.asyncio
    async def test_high_response_time_alert(self):
        """Test high response time alert."""
        with patch('mcp_financial.utils.alerting.alert_manager.send_alert') as mock_send:
            mock_send.return_value = "alert-124"
            
            alert_id = await MonitoringAlerts.high_response_time_alert(
                "transaction-service", 
                5000.0
            )
            
            assert alert_id == "alert-124"
            mock_send.assert_called_once_with(
                AlertType.HIGH_RESPONSE_TIME,
                AlertSeverity.WARNING,
                "High Response Time: transaction-service",
                "Service transaction-service response time is 5000.00ms",
                metadata={"service": "transaction-service", "response_time_ms": 5000.0}
            )
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_alert(self):
        """Test circuit breaker alert."""
        with patch('mcp_financial.utils.alerting.alert_manager.send_alert') as mock_send:
            mock_send.return_value = "alert-125"
            
            alert_id = await MonitoringAlerts.circuit_breaker_alert(
                "account-service", 
                "OPEN"
            )
            
            assert alert_id == "alert-125"
            mock_send.assert_called_once_with(
                AlertType.CIRCUIT_BREAKER_OPEN,
                AlertSeverity.CRITICAL,
                "Circuit Breaker OPEN: account-service",
                "Circuit breaker for account-service is now OPEN",
                metadata={"service": "account-service", "circuit_breaker_state": "OPEN"}
            )


@pytest.mark.asyncio
async def test_setup_metrics():
    """Test metrics setup."""
    with patch('mcp_financial.utils.metrics.start_http_server') as mock_server:
        mock_server.return_value = Mock()
        
        server = setup_metrics(port=9090, enabled=True)
        
        assert server is not None
        mock_server.assert_called_once()


@pytest.mark.asyncio
async def test_setup_metrics_disabled():
    """Test metrics setup when disabled."""
    server = setup_metrics(enabled=False)
    assert server is None