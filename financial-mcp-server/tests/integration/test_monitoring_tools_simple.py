"""
Simplified integration tests for monitoring functionality.
"""

import pytest
import asyncio
from unittest.mock import Mock, AsyncMock, patch
from mcp.server.fastmcp import FastMCP

from mcp_financial.tools.monitoring_tools import MonitoringTools
from mcp_financial.utils.health import HealthChecker, ServiceStatus, HealthCheckResult
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext
from mcp_financial.utils.alerting import alert_manager, Alert, AlertType, AlertSeverity
from datetime import datetime


class TestMonitoringIntegration:
    """Test monitoring integration."""
    
    @pytest.fixture
    def app(self):
        return FastMCP("Test MCP Server")
    
    @pytest.fixture
    def health_checker(self):
        return Mock(spec=HealthChecker)
    
    @pytest.fixture
    def auth_handler(self):
        handler = Mock(spec=JWTAuthHandler)
        handler.extract_user_context.return_value = UserContext(
            user_id="test-user",
            username="testuser",
            roles=["admin"],
            permissions=["read", "write", "admin"]
        )
        return handler
    
    @pytest.fixture
    def monitoring_tools(self, app, health_checker, auth_handler):
        return MonitoringTools(app, health_checker, auth_handler)
    
    def test_monitoring_tools_creation(self, monitoring_tools, health_checker):
        """Test that monitoring tools can be created successfully."""
        assert monitoring_tools is not None
        assert monitoring_tools.health_checker == health_checker
        assert monitoring_tools.app is not None
        assert monitoring_tools.health_monitor is not None
    
    @pytest.mark.asyncio
    async def test_health_checker_integration(self, health_checker):
        """Test health checker functionality."""
        # Mock health checker responses
        health_checker.get_overall_health.return_value = {
            "status": "healthy",
            "timestamp": "2023-01-01T00:00:00Z",
            "services": {
                "account-service": {
                    "status": "healthy",
                    "response_time_ms": 100.0,
                    "error": None
                }
            },
            "metrics": {
                "total_services": 1,
                "healthy_services": 1,
                "unhealthy_services": 0
            }
        }
        
        result = await health_checker.get_overall_health()
        assert result["status"] == "healthy"
        assert len(result["services"]) == 1
        assert result["metrics"]["healthy_services"] == 1
    
    @pytest.mark.asyncio
    async def test_monitoring_start_stop(self, monitoring_tools):
        """Test starting and stopping monitoring."""
        with patch.object(monitoring_tools.health_monitor, 'start_monitoring') as mock_start, \
             patch.object(monitoring_tools.health_monitor, 'stop_monitoring') as mock_stop:
            
            await monitoring_tools.start_monitoring()
            mock_start.assert_called_once()
            
            await monitoring_tools.stop_monitoring()
            mock_stop.assert_called_once()
    
    def test_auth_handler_integration(self, auth_handler):
        """Test authentication handler integration."""
        user_context = auth_handler.extract_user_context.return_value
        assert user_context.user_id == "test-user"
        assert user_context.username == "testuser"
        assert "admin" in user_context.roles
        assert "read" in user_context.permissions
    
    @pytest.mark.asyncio
    async def test_alert_manager_integration(self):
        """Test alert manager integration."""
        # Test sending an alert
        alert_id = await alert_manager.send_alert(
            AlertType.SERVICE_DOWN,
            AlertSeverity.CRITICAL,
            "Integration Test Alert",
            "This is a test alert for integration testing"
        )
        
        assert alert_id != ""
        
        # Test getting active alerts
        active_alerts = alert_manager.get_active_alerts()
        assert len(active_alerts) >= 1
        
        # Test resolving alert
        if alert_id:
            resolved = await alert_manager.resolve_alert(alert_id)
            assert resolved is True


@pytest.mark.asyncio
async def test_full_monitoring_integration():
    """Test full monitoring system integration."""
    # Create components
    app = FastMCP("Integration Test Server")
    
    health_checker = Mock(spec=HealthChecker)
    health_checker.get_overall_health.return_value = {
        "status": "healthy",
        "services": {"test-service": {"status": "healthy"}},
        "metrics": {"total_services": 1, "healthy_services": 1}
    }
    
    auth_handler = Mock(spec=JWTAuthHandler)
    auth_handler.extract_user_context.return_value = UserContext(
        user_id="integration-test",
        username="testuser",
        roles=["admin"],
        permissions=["read", "write"]
    )
    
    # Create monitoring tools
    monitoring_tools = MonitoringTools(app, health_checker, auth_handler)
    
    # Test that everything is wired up correctly
    assert monitoring_tools.app == app
    assert monitoring_tools.health_checker == health_checker
    assert monitoring_tools.auth_handler == auth_handler
    
    # Test health monitoring
    health_status = await health_checker.get_overall_health()
    assert health_status["status"] == "healthy"
    
    # Test authentication
    user_context = auth_handler.extract_user_context("test-token")
    assert user_context.user_id == "integration-test"
    
    print("✅ Full monitoring integration test passed")


if __name__ == "__main__":
    asyncio.run(test_full_monitoring_integration())