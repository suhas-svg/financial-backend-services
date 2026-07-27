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
        settings = MagicMock()
        settings.host = "127.0.0.1"
        settings.port = 8082
        settings.account_service_url = "http://localhost:8080"
        settings.transaction_service_url = "http://localhost:8081"
        settings.jwt_secret = "test-secret"
        settings.server_timeout = 5000
        settings.log_format = "text"
        settings.http_timeout = 5000
        settings.metrics_enabled = False
        settings.metrics_port = 9090
        settings.alert_webhook_url = None
        settings.slack_webhook_url = None
        settings.slack_channel = "#alerts"
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
        with patch.object(server, '_setup_plugin_manager', new_callable=AsyncMock) as mock_plugins, \
             patch.object(server, '_register_tools', new_callable=AsyncMock) as mock_register, \
             patch.object(server, '_setup_monitoring', new_callable=AsyncMock) as mock_monitoring, \
             patch.object(server, '_load_plugins', new_callable=AsyncMock) as mock_load:

            await server.initialize(MagicMock())

            mock_plugins.assert_called_once()
            mock_register.assert_called_once()
            mock_monitoring.assert_called_once()
            mock_load.assert_called_once()

    @pytest.mark.asyncio
    async def test_tool_registration(self, server):
        """Test MCP tool registration."""
        with patch('mcp_financial.server.AccountTools') as mock_account, \
             patch('mcp_financial.server.TransactionTools') as mock_transaction, \
             patch('mcp_financial.server.QueryTools') as mock_query, \
             patch('mcp_financial.server.MonitoringTools') as mock_monitoring:
            await server._register_tools()

            mock_account.assert_called_once_with(server.app, server.account_client, server.auth_handler)
            mock_transaction.assert_called_once_with(server.app, server.transaction_client, server.account_client, server.auth_handler)
            mock_query.assert_called_once_with(server.app, server.account_client, server.transaction_client, server.auth_handler)
            mock_monitoring.assert_called_once_with(server.app, server.health_checker, server.auth_handler)
            assert server.account_tools is mock_account.return_value

    @pytest.mark.asyncio
    async def test_monitoring_setup(self, server):
        """Test monitoring setup."""
        server.monitoring_tools = MagicMock()
        server.monitoring_tools.start_monitoring = AsyncMock()

        await server._setup_monitoring()
        server.monitoring_tools.start_monitoring.assert_awaited_once()

    def test_health_checker_is_configured(self, server):
        """Test health checker construction."""
        assert server.health_checker is not None

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

            with pytest.raises(Exception, match="Registration failed"):
                await server.initialize(MagicMock())

    def test_server_configuration_validation(self, mock_settings):
        """Test server configuration validation."""
        # Test with invalid settings
        mock_settings.account_service_url = ""

        with patch('mcp_financial.server.Settings', return_value=mock_settings):
            with pytest.raises(ValueError, match="Account service URL is required"):
                FinancialMCPServer()

    @pytest.mark.asyncio
    async def test_registered_tool_groups(self, server):
        """Test all financial tool groups are registered."""
        await server._register_tools()
        assert server.account_tools is not None
        assert server.transaction_tools is not None
        assert server.query_tools is not None

    def test_metrics_configuration_is_retained(self, server):
        """Test metrics setup completes during construction."""
        assert hasattr(server, "metrics_server")

    def test_server_context_manager(self, mock_settings):
        """Test server as async context manager."""
        with patch('mcp_financial.server.Settings', return_value=mock_settings), \
             patch('mcp_financial.server.AccountServiceClient', autospec=True), \
             patch('mcp_financial.server.TransactionServiceClient', autospec=True), \
             patch('mcp_financial.server.JWTAuthHandler'):

            async def test_context():
                async with FinancialMCPServer() as server:
                    assert server is not None
                    assert hasattr(server, 'app')

            # Run the context manager test
            asyncio.run(test_context())


@pytest.mark.skip(reason="requires live downstream service integration")
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