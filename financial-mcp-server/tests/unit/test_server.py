"""
Unit tests for the main MCP server implementation.
"""

import pytest
import asyncio
from unittest.mock import AsyncMock, MagicMock, patch
from mcp.server.fastmcp import FastMCP
from mcp.server.models import InitializationOptions

from mcp_financial.server import FinancialMCPServer
from mcp_financial.config.settings import Settings


class TestFinancialMCPServer:
    """Test suite for FinancialMCPServer class."""
    
    @pytest.fixture
    def mock_settings(self):
        """Mock settings for testing."""
        settings = MagicMock(spec=Settings)
        settings.account_service_url = "http://localhost:8080"
        settings.transaction_service_url = "http://localhost:8081"
        settings.jwt_secret = "test-secret"
        settings.server_timeout = 5000
        settings.log_level = "INFO"
        return settings
    
    @pytest.fixture
    def server(self, mock_settings):
        """Create FinancialMCPServer instance for testing."""
        with patch('mcp_financial.server.Settings', return_value=mock_settings), \
             patch('mcp_financial.server.AccountServiceClient'), \
             patch('mcp_financial.server.TransactionServiceClient'), \
             patch('mcp_financial.server.JWTAuthHandler'), \
             patch('mcp_financial.server.AccountTools'), \
             patch('mcp_financial.server.TransactionTools'), \
             patch('mcp_financial.server.QueryTools'), \
             patch('mcp_financial.server.MonitoringTools'):
            
            server = FinancialMCPServer()
            return server
    
    def test_server_initialization(self, server, mock_settings):
        """Test server initialization."""
        assert server.settings == mock_settings
        assert server.app is not None
        assert hasattr(server, 'account_client')
        assert hasattr(server, 'transaction_client')
        assert hasattr(server, 'auth_handler')
    
    @pytest.mark.asyncio
    async def test_server_startup(self, server):
        """Test server startup process."""
        with patch.object(server, '_register_tools', new_callable=AsyncMock) as mock_register, \
             patch.object(server, '_setup_monitoring', new_callable=AsyncMock) as mock_monitoring, \
             patch.object(server, '_setup_health_checks', new_callable=AsyncMock) as mock_health:
            
            options = InitializationOptions(
                server_name="financial-mcp-server",
                server_version="1.0.0"
            )
            
            await server.initialize(options)
            
            mock_register.assert_called_once()
            mock_monitoring.assert_called_once()
            mock_health.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_tool_registration(self, server):
        """Test MCP tool registration."""
        with patch.object(server.account_tools, 'register_tools', new_callable=AsyncMock) as mock_account, \
             patch.object(server.transaction_tools, 'register_tools', new_callable=AsyncMock) as mock_transaction, \
             patch.object(server.query_tools, 'register_tools', new_callable=AsyncMock) as mock_query, \
             patch.object(server.monitoring_tools, 'register_tools', new_callable=AsyncMock) as mock_monitoring:
            
            await server._register_tools()
            
            mock_account.assert_called_once()
            mock_transaction.assert_called_once()
            mock_query.assert_called_once()
            mock_monitoring.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_monitoring_setup(self, server):
        """Test monitoring setup."""
        with patch('mcp_financial.server.start_http_server') as mock_prometheus, \
             patch('mcp_financial.server.setup_structured_logging') as mock_logging:
            
            await server._setup_monitoring()
            
            mock_prometheus.assert_called_once()
            mock_logging.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_health_checks_setup(self, server):
        """Test health checks setup."""
        with patch.object(server.app, 'tool') as mock_tool:
            await server._setup_health_checks()
            
            # Verify health check tool was registered
            mock_tool.assert_called()
    
    @pytest.mark.asyncio
    async def test_server_shutdown(self, server):
        """Test server shutdown process."""
        with patch.object(server.account_client, 'close', new_callable=AsyncMock) as mock_account_close, \
             patch.object(server.transaction_client, 'close', new_callable=AsyncMock) as mock_transaction_close:
            
            await server.shutdown()
            
            mock_account_close.assert_called_once()
            mock_transaction_close.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_error_handling_during_startup(self, server):
        """Test error handling during server startup."""
        with patch.object(server, '_register_tools', new_callable=AsyncMock) as mock_register:
            mock_register.side_effect = Exception("Registration failed")
            
            options = InitializationOptions(
                server_name="financial-mcp-server",
                server_version="1.0.0"
            )
            
            with pytest.raises(Exception, match="Registration failed"):
                await server.initialize(options)
    
    def test_server_configuration_validation(self, mock_settings):
        """Test server configuration validation."""
        # Test with invalid settings
        mock_settings.account_service_url = ""
        
        with patch('mcp_financial.server.Settings', return_value=mock_settings):
            with pytest.raises(ValueError, match="Account service URL is required"):
                FinancialMCPServer()
    
    @pytest.mark.asyncio
    async def test_concurrent_tool_execution(self, server):
        """Test concurrent execution of multiple tools."""
        # Mock tool execution
        with patch.object(server.account_tools, 'create_account', new_callable=AsyncMock) as mock_create, \
             patch.object(server.transaction_tools, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
            
            mock_create.return_value = {"id": "acc_123"}
            mock_deposit.return_value = {"id": "txn_456"}
            
            # Execute tools concurrently
            tasks = [
                server.account_tools.create_account("user1", "CHECKING", 0.0, "token1"),
                server.transaction_tools.deposit_funds("acc_123", 100.0, "deposit", "token1")
            ]
            
            results = await asyncio.gather(*tasks)
            
            assert len(results) == 2
            assert results[0]["id"] == "acc_123"
            assert results[1]["id"] == "txn_456"
    
    @pytest.mark.asyncio
    async def test_server_metrics_collection(self, server):
        """Test metrics collection during server operation."""
        with patch('mcp_financial.server.mcp_requests_total') as mock_counter, \
             patch('mcp_financial.server.mcp_request_duration') as mock_histogram:
            
            # Simulate tool execution with metrics
            await server._execute_with_metrics("test_tool", lambda: {"success": True})
            
            mock_counter.labels.assert_called_with(tool="test_tool", status="success")
            mock_histogram.labels.assert_called_with(tool="test_tool")
    
    def test_server_context_manager(self, mock_settings):
        """Test server as async context manager."""
        with patch('mcp_financial.server.Settings', return_value=mock_settings), \
             patch('mcp_financial.server.AccountServiceClient'), \
             patch('mcp_financial.server.TransactionServiceClient'), \
             patch('mcp_financial.server.JWTAuthHandler'):
            
            async def test_context():
                async with FinancialMCPServer() as server:
                    assert server is not None
                    assert hasattr(server, 'app')
            
            # Run the context manager test
            asyncio.run(test_context())


class TestServerIntegration:
    """Integration tests for server components."""
    
    @pytest.fixture
    def integration_server(self):
        """Create server for integration testing."""
        with patch('mcp_financial.server.Settings') as mock_settings_class:
            mock_settings = MagicMock()
            mock_settings.account_service_url = "http://localhost:8080"
            mock_settings.transaction_service_url = "http://localhost:8081"
            mock_settings.jwt_secret = "test-secret"
            mock_settings_class.return_value = mock_settings
            
            server = FinancialMCPServer()
            return server
    
    @pytest.mark.asyncio
    async def test_full_server_lifecycle(self, integration_server):
        """Test complete server lifecycle."""
        # Mock all external dependencies
        with patch.object(integration_server.account_client, 'health_check', new_callable=AsyncMock) as mock_health, \
             patch.object(integration_server.transaction_client, 'health_check', new_callable=AsyncMock) as mock_health2:
            
            mock_health.return_value = True
            mock_health2.return_value = True
            
            # Initialize server
            options = InitializationOptions(
                server_name="financial-mcp-server",
                server_version="1.0.0"
            )
            
            await integration_server.initialize(options)
            
            # Verify server is ready
            assert integration_server.app is not None
            
            # Test health check
            health_status = await integration_server.get_health_status()
            assert health_status["status"] == "healthy"
            
            # Shutdown server
            await integration_server.shutdown()
    
    @pytest.mark.asyncio
    async def test_service_dependency_health_checks(self, integration_server):
        """Test health checks for service dependencies."""
        with patch.object(integration_server.account_client, 'health_check', new_callable=AsyncMock) as mock_account_health, \
             patch.object(integration_server.transaction_client, 'health_check', new_callable=AsyncMock) as mock_transaction_health:
            
            # Test healthy services
            mock_account_health.return_value = True
            mock_transaction_health.return_value = True
            
            health = await integration_server.check_service_health()
            
            assert health["account_service"] is True
            assert health["transaction_service"] is True
            assert health["overall"] is True
            
            # Test unhealthy service
            mock_account_health.return_value = False
            
            health = await integration_server.check_service_health()
            
            assert health["account_service"] is False
            assert health["transaction_service"] is True
            assert health["overall"] is False
    
    @pytest.mark.asyncio
    async def test_authentication_integration(self, integration_server):
        """Test authentication integration across tools."""
        valid_token = "Bearer valid.jwt.token"
        invalid_token = "Bearer invalid.jwt.token"
        
        with patch.object(integration_server.auth_handler, 'extract_user_context') as mock_auth:
            # Test valid authentication
            mock_auth.return_value = MagicMock(
                user_id="test_user",
                roles=["customer"],
                permissions=["account:read"]
            )
            
            # This should succeed
            result = await integration_server.authenticate_request(valid_token)
            assert result is not None
            
            # Test invalid authentication
            from mcp_financial.auth.jwt_handler import AuthenticationError
            mock_auth.side_effect = AuthenticationError("Invalid token")
            
            with pytest.raises(AuthenticationError):
                await integration_server.authenticate_request(invalid_token)
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_integration(self, integration_server):
        """Test circuit breaker integration with service clients."""
        with patch.object(integration_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
            # Simulate service failures
            mock_get.side_effect = Exception("Service unavailable")
            
            # Multiple failures should trigger circuit breaker
            for _ in range(5):
                try:
                    await integration_server.account_client.get_account("acc_123", "token")
                except:
                    pass
            
            # Circuit breaker should now be open
            assert integration_server.account_client.circuit_breaker.state == "OPEN"
    
    @pytest.mark.asyncio
    async def test_error_propagation(self, integration_server):
        """Test error propagation through server layers."""
        with patch.object(integration_server.account_client, 'create_account', new_callable=AsyncMock) as mock_create:
            # Simulate service error
            mock_create.side_effect = Exception("Database connection failed")
            
            # Error should be caught and wrapped appropriately
            try:
                await integration_server.account_tools.create_account(
                    "user123", "CHECKING", 0.0, "valid_token"
                )
            except Exception as e:
                assert "Database connection failed" in str(e)