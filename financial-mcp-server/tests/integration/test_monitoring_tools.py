"""
Integration tests for monitoring MCP tools.
"""

import pytest
import asyncio
from unittest.mock import Mock, AsyncMock, patch
from mcp.server.fastmcp import FastMCP
from mcp.types import TextContent

from mcp_financial.tools.monitoring_tools import MonitoringTools
from mcp_financial.utils.health import HealthChecker, ServiceStatus, HealthCheckResult
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext
from mcp_financial.utils.alerting import alert_manager, Alert, AlertType, AlertSeverity
from datetime import datetime


class TestMonitoringToolsIntegration:
    """Test monitoring tools integration."""
    
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
    
    @pytest.mark.asyncio
    async def test_health_check_tool_success(self, monitoring_tools, health_checker):
        """Test health check tool with successful response."""
        # Mock health checker response
        health_checker.get_overall_health.return_value = {
            "status": "healthy",
            "timestamp": "2023-01-01T00:00:00Z",
            "services": {
                "account-service": {
                    "status": "healthy",
                    "response_time_ms": 100.0,
                    "error": None,
                    "timestamp": "2023-01-01T00:00:00Z",
                    "details": None
                },
                "transaction-service": {
                    "status": "healthy",
                    "response_time_ms": 150.0,
                    "error": None,
                    "timestamp": "2023-01-01T00:00:00Z",
                    "details": None
                }
            },
            "metrics": {
                "total_services": 2,
                "healthy_services": 2,
                "unhealthy_services": 0,
                "average_response_time_ms": 125.0
            }
        }
        
        # Get the health_check tool function using the new method
        tool_functions = monitoring_tools.get_tool_functions()
        health_check_func = tool_functions['health_check']
        
        assert health_check_func is not None
        
        # Call the tool
        result = await health_check_func(auth_token="valid-token")
        
        assert isinstance(result, list)
        assert len(result) == 1
        assert isinstance(result[0], TextContent)
        
        response_text = result[0].text
        assert "Overall Status: HEALTHY" in response_text
        assert "account-service: healthy" in response_text
        assert "transaction-service: healthy" in response_text
        assert "Total Services: 2" in response_text
        assert "Healthy Services: 2" in response_text
    
    @pytest.mark.asyncio
    async def test_health_check_tool_unhealthy(self, monitoring_tools, health_checker):
        """Test health check tool with unhealthy services."""
        # Mock health checker response with unhealthy service
        health_checker.get_overall_health.return_value = {
            "status": "unhealthy",
            "timestamp": "2023-01-01T00:00:00Z",
            "services": {
                "account-service": {
                    "status": "healthy",
                    "response_time_ms": 100.0,
                    "error": None,
                    "timestamp": "2023-01-01T00:00:00Z",
                    "details": None
                },
                "transaction-service": {
                    "status": "unhealthy",
                    "response_time_ms": None,
                    "error": "Connection timeout",
                    "timestamp": "2023-01-01T00:00:00Z",
                    "details": None
                }
            },
            "metrics": {
                "total_services": 2,
                "healthy_services": 1,
                "unhealthy_services": 1,
                "average_response_time_ms": 100.0
            }
        }
        
        # Get the health_check tool function using the new method
        tool_functions = monitoring_tools.get_tool_functions()
        health_check_func = tool_functions['health_check']
        
        # Call the tool
        result = await health_check_func(auth_token="valid-token")
        
        response_text = result[0].text
        assert "Overall Status: UNHEALTHY" in response_text
        assert "❌ transaction-service: unhealthy" in response_text
        assert "Error: Connection timeout" in response_text
        assert "Unhealthy Services: 1" in response_text
    
    @pytest.mark.asyncio
    async def test_get_metrics_tool(self, monitoring_tools):
        """Test get metrics tool."""
        with patch('mcp_financial.tools.monitoring_tools.get_metrics_summary') as mock_metrics:
            mock_metrics.return_value = {
                "total_requests": 100,
                "active_connections": 5,
                "total_errors": 2,
                "auth_requests": 50,
                "auth_failures": 1,
                "query_operations": {
                    "transaction_history": 20,
                    "transaction_search": 15,
                    "account_analytics": 10,
                    "transaction_limits": 5,
                    "query_errors": 0
                }
            }
            
            # Get the get_metrics tool function using the new method
            tool_functions = monitoring_tools.get_tool_functions()
            get_metrics_func = tool_functions['get_metrics']
            
            # Call the tool
            result = await get_metrics_func(auth_token="valid-token")
            
            response_text = result[0].text
            assert "Total Requests: 100" in response_text
            assert "Active Connections: 5" in response_text
            assert "Total Errors: 2" in response_text
            assert "Auth Requests: 50" in response_text
            assert "Auth Failures: 1" in response_text
            assert "Transaction History: 20" in response_text
    
    @pytest.mark.asyncio
    async def test_get_service_status_specific_service(self, monitoring_tools, health_checker):
        """Test get service status for specific service."""
        # Mock health checker response for account service
        health_checker.check_account_service.return_value = HealthCheckResult(
            service="account-service",
            status=ServiceStatus.HEALTHY,
            response_time_ms=100.0,
            error=None,
            timestamp=datetime.utcnow(),
            details={"endpoint": "/actuator/health", "status_code": 200}
        )
        
        # Get the get_service_status tool function using the new method
        tool_functions = monitoring_tools.get_tool_functions()
        get_service_status_func = tool_functions['get_service_status']
        
        # Call the tool
        result = await get_service_status_func(
            service_name="account-service",
            auth_token="valid-token"
        )
        
        response_text = result[0].text
        assert "Service: account-service" in response_text
        assert "Status: healthy" in response_text
        assert "Response Time: 100.00ms" in response_text
    
    @pytest.mark.asyncio
    async def test_get_service_status_all_services(self, monitoring_tools, health_checker):
        """Test get service status for all services."""
        # Mock health checker response for all services
        health_checker.check_all_services.return_value = {
            "account-service": HealthCheckResult(
                service="account-service",
                status=ServiceStatus.HEALTHY,
                response_time_ms=100.0,
                error=None,
                timestamp=datetime.utcnow()
            ),
            "transaction-service": HealthCheckResult(
                service="transaction-service",
                status=ServiceStatus.DEGRADED,
                response_time_ms=500.0,
                error="Slow response",
                timestamp=datetime.utcnow()
            )
        }
        
        # Get the get_service_status tool function using the new method
        tool_functions = monitoring_tools.get_tool_functions()
        get_service_status_func = tool_functions['get_service_status']
        
        # Call the tool without service_name (all services)
        result = await get_service_status_func(auth_token="valid-token")
        
        response_text = result[0].text
        assert "All Services Status:" in response_text
        assert "✅ account-service:" in response_text
        assert "Status: healthy" in response_text
        assert "transaction-service:" in response_text
        assert "Status: degraded" in response_text
        assert "Error: Slow response" in response_text
    
    @pytest.mark.asyncio
    async def test_get_alerts_tool(self, monitoring_tools):
        """Test get alerts tool."""
        # Add some mock alerts to the alert manager
        test_alerts = [
            Alert(
                id="alert-1",
                type=AlertType.SERVICE_DOWN,
                severity=AlertSeverity.CRITICAL,
                title="Service Down",
                description="Account service is not responding",
                timestamp=datetime.utcnow(),
                source="mcp-financial-server",
                metadata={"service": "account-service"}
            ),
            Alert(
                id="alert-2",
                type=AlertType.HIGH_RESPONSE_TIME,
                severity=AlertSeverity.WARNING,
                title="High Response Time",
                description="Transaction service response time is high",
                timestamp=datetime.utcnow(),
                source="mcp-financial-server",
                metadata={"service": "transaction-service", "response_time": 5000}
            )
        ]
        
        with patch.object(alert_manager, 'get_alert_history') as mock_history:
            mock_history.return_value = test_alerts
            
            # Get the get_alerts tool function using the new method
            tool_functions = monitoring_tools.get_tool_functions()
            get_alerts_func = tool_functions['get_alerts']
            
            # Call the tool
            result = await get_alerts_func(limit=10, auth_token="valid-token")
            
            response_text = result[0].text
            assert "Recent Alerts (2):" in response_text
            assert "🔴 Service Down" in response_text
            assert "🟡 High Response Time" in response_text
            assert "Type: service_down" in response_text
            assert "Severity: critical" in response_text
    
    @pytest.mark.asyncio
    async def test_get_monitoring_summary_tool(self, monitoring_tools, health_checker):
        """Test comprehensive monitoring summary tool."""
        # Mock all required data
        health_checker.get_overall_health.return_value = {
            "status": "healthy",
            "timestamp": "2023-01-01T00:00:00Z",
            "services": {
                "account-service": {"status": "healthy"},
                "transaction-service": {"status": "healthy"}
            }
        }
        
        with patch('mcp_financial.tools.monitoring_tools.get_metrics_summary') as mock_metrics, \
             patch.object(alert_manager, 'get_alert_stats') as mock_alert_stats, \
             patch.object(monitoring_tools.health_monitor, 'get_health_summary') as mock_health_summary:
            
            mock_metrics.return_value = {
                "total_requests": 100,
                "active_connections": 5,
                "total_errors": 2,
                "auth_requests": 50,
                "auth_failures": 1,
                "query_operations": {}
            }
            
            mock_alert_stats.return_value = {
                "active_alerts": 1,
                "total_alerts_24h": 5,
                "alerts_by_severity_24h": {"critical": 1, "warning": 4}
            }
            
            mock_health_summary.return_value = {
                "uptime_stats": {
                    "account-service": {"uptime_percentage": 99.5},
                    "transaction-service": {"uptime_percentage": 98.2}
                }
            }
            
            # Get the get_monitoring_summary tool function using the new method
            tool_functions = monitoring_tools.get_tool_functions()
            get_summary_func = tool_functions['get_monitoring_summary']
            
            # Call the tool
            result = await get_summary_func(auth_token="valid-token")
            
            response_text = result[0].text
            assert "MCP Financial Server Monitoring Summary" in response_text
            assert "Overall Status: HEALTHY" in response_text
            assert "Total Requests: 100" in response_text
            assert "Active Alerts: 1" in response_text
            assert "account-service: 99.5%" in response_text
    
    @pytest.mark.asyncio
    async def test_authentication_failure(self, monitoring_tools, auth_handler):
        """Test tool behavior with authentication failure."""
        # Mock authentication failure
        from mcp_financial.auth.jwt_handler import AuthenticationError
        auth_handler.extract_user_context.side_effect = AuthenticationError("Invalid token")
        
        # Get the health_check tool function using the new method
        tool_functions = monitoring_tools.get_tool_functions()
        health_check_func = tool_functions['health_check']
        
        # Call the tool with invalid token
        result = await health_check_func(auth_token="invalid-token")
        
        response_text = result[0].text
        assert "Authentication failed" in response_text
    
    @pytest.mark.asyncio
    async def test_tool_without_authentication(self, monitoring_tools, health_checker):
        """Test tool behavior without authentication token."""
        # Mock health checker response
        health_checker.get_overall_health.return_value = {
            "status": "healthy",
            "timestamp": "2023-01-01T00:00:00Z",
            "services": {},
            "metrics": {
                "total_services": 0, 
                "healthy_services": 0, 
                "unhealthy_services": 0,
                "average_response_time_ms": None
            }
        }
        
        # Get the health_check tool function using the new method
        tool_functions = monitoring_tools.get_tool_functions()
        health_check_func = tool_functions['health_check']
        
        # Call the tool without auth token
        result = await health_check_func()
        
        # Should still work (auth is optional for monitoring tools)
        assert isinstance(result, list)
        assert len(result) == 1
        response_text = result[0].text
        assert "Overall Status: HEALTHY" in response_text
    
    @pytest.mark.asyncio
    async def test_start_stop_monitoring(self, monitoring_tools):
        """Test starting and stopping monitoring."""
        with patch.object(monitoring_tools.health_monitor, 'start_monitoring') as mock_start, \
             patch.object(monitoring_tools.health_monitor, 'stop_monitoring') as mock_stop:
            
            await monitoring_tools.start_monitoring()
            mock_start.assert_called_once()
            
            await monitoring_tools.stop_monitoring()
            mock_stop.assert_called_once()


@pytest.mark.asyncio
async def test_monitoring_tools_error_handling():
    """Test error handling in monitoring tools."""
    app = FastMCP("Test MCP Server")
    health_checker = Mock(spec=HealthChecker)
    auth_handler = Mock(spec=JWTAuthHandler)
    
    # Mock health checker to raise exception
    health_checker.get_overall_health.side_effect = Exception("Health check failed")
    
    monitoring_tools = MonitoringTools(app, health_checker, auth_handler)
    
    # Get the health_check tool function using the new method
    tool_functions = monitoring_tools.get_tool_functions()
    health_check_func = tool_functions['health_check']
    
    # Call the tool
    result = await health_check_func()
    
    response_text = result[0].text
    assert "Health check failed" in response_text