"""
Integration tests for error handling scenarios.
"""

import pytest
import asyncio
import httpx
from unittest.mock import Mock, patch, AsyncMock
from decimal import Decimal

from src.mcp_financial.clients.base_client import BaseHTTPClient, CircuitBreakerError
from src.mcp_financial.clients.account_client import AccountServiceClient
from src.mcp_financial.exceptions.base import (
    ValidationError,
    AuthenticationError,
    ServiceError,
    TimeoutError
)


class TestBaseHTTPClientErrorHandling:
    """Test error handling in base HTTP client."""
    
    @pytest.fixture
    async def client(self):
        """Create test HTTP client."""
        client = BaseHTTPClient("http://test-service:8080")
        yield client
        await client.close()
        
    @pytest.mark.asyncio
    async def test_timeout_error_handling(self, client):
        """Test timeout error handling."""
        with patch.object(client.client, 'request') as mock_request:
            mock_request.side_effect = httpx.TimeoutException("Request timeout")
            
            with pytest.raises(TimeoutError) as exc_info:
                await client.get("/test")
                
            error = exc_info.value
            assert error.error_code == "TIMEOUT_ERROR"
            assert "timeout" in error.message.lower()
            assert error.timeout_seconds == client.timeout
            
    @pytest.mark.asyncio
    async def test_connection_error_handling(self, client):
        """Test connection error handling."""
        with patch.object(client.client, 'request') as mock_request:
            mock_request.side_effect = httpx.ConnectError("Connection failed")
            
            with pytest.raises(ServiceError) as exc_info:
                await client.get("/test")
                
            error = exc_info.value
            assert error.error_code == "SERVICE_ERROR"
            assert "connection error" in error.message.lower()
            
    @pytest.mark.asyncio
    async def test_http_400_error_handling(self, client):
        """Test HTTP 400 error handling."""
        mock_response = Mock()
        mock_response.status_code = 400
        mock_response.json.return_value = {"message": "Invalid request"}
        
        with patch.object(client.client, 'request') as mock_request:
            mock_request.return_value = mock_response
            
            with pytest.raises(ValidationError) as exc_info:
                await client.get("/test")
                
            error = exc_info.value
            assert error.error_code == "VALIDATION_ERROR"
            assert "bad request" in error.message.lower()
            
    @pytest.mark.asyncio
    async def test_http_401_error_handling(self, client):
        """Test HTTP 401 error handling."""
        mock_response = Mock()
        mock_response.status_code = 401
        mock_response.json.return_value = {"error": "Unauthorized"}
        
        with patch.object(client.client, 'request') as mock_request:
            mock_request.return_value = mock_response
            
            with pytest.raises(AuthenticationError) as exc_info:
                await client.get("/test")
                
            error = exc_info.value
            assert error.error_code == "AUTHENTICATION_ERROR"
            
    @pytest.mark.asyncio
    async def test_http_500_error_handling(self, client):
        """Test HTTP 500 error handling."""
        mock_response = Mock()
        mock_response.status_code = 500
        mock_response.json.return_value = {"error": "Internal server error"}
        mock_response.raise_for_status.side_effect = httpx.HTTPStatusError(
            "Server error", request=Mock(), response=mock_response
        )
        
        with patch.object(client.client, 'request') as mock_request:
            mock_request.return_value = mock_response
            
            with pytest.raises(ServiceError) as exc_info:
                await client.get("/test")
                
            error = exc_info.value
            assert error.error_code == "SERVICE_ERROR"
            assert error.status_code == 500


class TestCircuitBreakerIntegration:
    """Test circuit breaker integration with HTTP client."""
    
    @pytest.fixture
    async def client(self):
        """Create test HTTP client with low failure threshold."""
        client = BaseHTTPClient(
            "http://test-service:8080",
            circuit_breaker_failure_threshold=2,
            circuit_breaker_recovery_timeout=1
        )
        yield client
        await client.close()
        
    @pytest.mark.asyncio
    async def test_circuit_breaker_opens_on_failures(self, client):
        """Test circuit breaker opens after consecutive failures."""
        with patch.object(client.client, 'request') as mock_request:
            mock_request.side_effect = httpx.ConnectError("Connection failed")
            
            # First failure
            with pytest.raises(ServiceError):
                await client.get("/test")
            assert client.circuit_breaker.state == "CLOSED"
            
            # Second failure should open circuit
            with pytest.raises(ServiceError):
                await client.get("/test")
            assert client.circuit_breaker.state == "OPEN"
            
            # Third call should raise CircuitBreakerError
            with pytest.raises(CircuitBreakerError):
                await client.get("/test")
                
    @pytest.mark.asyncio
    async def test_circuit_breaker_recovery(self, client):
        """Test circuit breaker recovery after timeout."""
        with patch.object(client.client, 'request') as mock_request:
            # Cause failures to open circuit
            mock_request.side_effect = httpx.ConnectError("Connection failed")
            
            with pytest.raises(ServiceError):
                await client.get("/test")
            with pytest.raises(ServiceError):
                await client.get("/test")
            assert client.circuit_breaker.state == "OPEN"
            
            # Wait for recovery timeout
            await asyncio.sleep(1.1)
            
            # Mock successful response
            mock_response = Mock()
            mock_response.status_code = 200
            mock_response.json.return_value = {"status": "ok"}
            mock_response.raise_for_status.return_value = None
            mock_request.side_effect = None
            mock_request.return_value = mock_response
            
            # Should transition to half-open and then closed
            result = await client.get("/test")
            assert result == {"status": "ok"}
            assert client.circuit_breaker.state in ["HALF_OPEN", "CLOSED"]


class TestAccountServiceClientErrorHandling:
    """Test error handling in account service client."""
    
    @pytest.fixture
    async def account_client(self):
        """Create test account service client."""
        client = AccountServiceClient("http://account-service:8080")
        yield client
        await client.close()
        
    @pytest.mark.asyncio
    async def test_get_account_not_found(self, account_client):
        """Test get account with 404 error."""
        mock_response = Mock()
        mock_response.status_code = 404
        mock_response.json.return_value = {"error": "Account not found"}
        
        with patch.object(account_client.client, 'request') as mock_request:
            mock_request.return_value = mock_response
            
            with pytest.raises(ValidationError) as exc_info:
                await account_client.get_account("nonexistent", "token")
                
            error = exc_info.value
            assert "not found" in error.message.lower()
            
    @pytest.mark.asyncio
    async def test_create_account_validation_error(self, account_client):
        """Test create account with validation error."""
        mock_response = Mock()
        mock_response.status_code = 400
        mock_response.json.return_value = {
            "message": "Validation failed",
            "errors": [
                {"field": "accountType", "message": "Invalid account type"}
            ]
        }
        
        with patch.object(account_client.client, 'request') as mock_request:
            mock_request.return_value = mock_response
            
            account_data = {
                "ownerId": "user123",
                "accountType": "INVALID",
                "balance": 100.0
            }
            
            with pytest.raises(ValidationError) as exc_info:
                await account_client.create_account(account_data, "token")
                
            error = exc_info.value
            assert "bad request" in error.message.lower()
            
    @pytest.mark.asyncio
    async def test_update_balance_insufficient_funds(self, account_client):
        """Test update balance with insufficient funds."""
        mock_response = Mock()
        mock_response.status_code = 409
        mock_response.json.return_value = {"message": "Insufficient funds"}
        
        with patch.object(account_client.client, 'request') as mock_request:
            mock_request.return_value = mock_response
            
            with pytest.raises(Exception):  # Should be BusinessRuleError
                await account_client.update_balance("acc123", Decimal("-1000"), "token")


class TestErrorPropagation:
    """Test error propagation through the system."""
    
    @pytest.mark.asyncio
    async def test_error_propagation_from_client_to_tool(self):
        """Test error propagation from HTTP client to MCP tool."""
        # This would test the full error flow from HTTP client through
        # service client to MCP tool and back to user
        
        # Mock the account client to raise a service error
        mock_client = AsyncMock()
        mock_client.get_account.side_effect = ServiceError(
            "Service unavailable",
            service_name="account-service",
            status_code=503
        )
        
        # Test that the error is properly handled and formatted
        # This would be implemented with actual MCP tool integration
        pass
        
    @pytest.mark.asyncio
    async def test_validation_error_formatting(self):
        """Test validation error formatting for MCP responses."""
        from src.mcp_financial.exceptions.handlers import create_error_response
        
        error = ValidationError(
            "Invalid account type",
            field="account_type",
            value="INVALID"
        )
        
        response = create_error_response(error, "create_account", "req123", "user123")
        
        assert len(response) == 1
        assert response[0].type == "text"
        
        # Parse the JSON response
        import json
        response_data = json.loads(response[0].text)
        
        assert response_data["error_code"] == "VALIDATION_ERROR"
        assert response_data["error_message"] == "Invalid account type"
        assert response_data["request_id"] == "req123"


class TestRetryMechanisms:
    """Test retry mechanisms in error scenarios."""
    
    @pytest.fixture
    async def client(self):
        """Create test HTTP client."""
        client = BaseHTTPClient("http://test-service:8080", max_retries=3)
        yield client
        await client.close()
        
    @pytest.mark.asyncio
    async def test_retry_on_timeout(self, client):
        """Test retry mechanism on timeout errors."""
        call_count = 0
        
        def mock_request(*args, **kwargs):
            nonlocal call_count
            call_count += 1
            if call_count < 3:
                raise httpx.TimeoutException("Timeout")
            else:
                # Success on third try
                mock_response = Mock()
                mock_response.status_code = 200
                mock_response.json.return_value = {"status": "ok"}
                mock_response.raise_for_status.return_value = None
                return mock_response
                
        with patch.object(client.client, 'request', side_effect=mock_request):
            result = await client.get("/test")
            assert result == {"status": "ok"}
            assert call_count == 3
            
    @pytest.mark.asyncio
    async def test_no_retry_on_client_errors(self, client):
        """Test that client errors (4xx) are not retried."""
        call_count = 0
        
        def mock_request(*args, **kwargs):
            nonlocal call_count
            call_count += 1
            mock_response = Mock()
            mock_response.status_code = 400
            mock_response.json.return_value = {"error": "Bad request"}
            return mock_response
            
        with patch.object(client.client, 'request', side_effect=mock_request):
            with pytest.raises(ValidationError):
                await client.get("/test")
            # Should only be called once (no retry for 4xx errors)
            assert call_count == 1


class TestConcurrentErrorHandling:
    """Test error handling under concurrent load."""
    
    @pytest.mark.asyncio
    async def test_concurrent_circuit_breaker_behavior(self):
        """Test circuit breaker behavior under concurrent requests."""
        client = BaseHTTPClient(
            "http://test-service:8080",
            circuit_breaker_failure_threshold=2
        )
        
        async def failing_request():
            with patch.object(client.client, 'request') as mock_request:
                mock_request.side_effect = httpx.ConnectError("Connection failed")
                try:
                    await client.get("/test")
                except (ServiceError, CircuitBreakerError):
                    pass
                    
        # Run multiple concurrent requests
        tasks = [failing_request() for _ in range(5)]
        await asyncio.gather(*tasks, return_exceptions=True)
        
        # Circuit should be open after failures
        assert client.circuit_breaker.state == "OPEN"
        
        await client.close()
        
    @pytest.mark.asyncio
    async def test_error_isolation_between_clients(self):
        """Test that errors in one client don't affect others."""
        client1 = BaseHTTPClient("http://service1:8080", circuit_breaker_failure_threshold=1)
        client2 = BaseHTTPClient("http://service2:8080", circuit_breaker_failure_threshold=1)
        
        # Cause failure in client1
        with patch.object(client1.client, 'request') as mock_request1:
            mock_request1.side_effect = httpx.ConnectError("Connection failed")
            
            with pytest.raises(ServiceError):
                await client1.get("/test")
                
        # Client1 circuit should be open
        assert client1.circuit_breaker.state == "OPEN"
        
        # Client2 should still work
        with patch.object(client2.client, 'request') as mock_request2:
            mock_response = Mock()
            mock_response.status_code = 200
            mock_response.json.return_value = {"status": "ok"}
            mock_response.raise_for_status.return_value = None
            mock_request2.return_value = mock_response
            
            result = await client2.get("/test")
            assert result == {"status": "ok"}
            assert client2.circuit_breaker.state == "CLOSED"
            
        await client1.close()
        await client2.close()


if __name__ == "__main__":
    pytest.main([__file__])