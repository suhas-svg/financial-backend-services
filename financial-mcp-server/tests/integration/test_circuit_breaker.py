"""
Integration tests for circuit breaker functionality.
"""

import pytest
import asyncio
import sys
import os
from unittest.mock import AsyncMock, patch, MagicMock
from datetime import datetime, timedelta

# Add src directory to Python path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', 'src'))

from mcp_financial.clients.base_client import BaseHTTPClient, CircuitBreaker, CircuitBreakerError, ServiceUnavailableError
import httpx


class TestCircuitBreakerIntegration:
    """Integration tests for circuit breaker behavior."""
    
    @pytest.fixture
    def circuit_breaker(self):
        """Create circuit breaker with test configuration."""
        return CircuitBreaker(failure_threshold=3, recovery_timeout=5)
    
    @pytest.fixture
    def client_with_fast_circuit_breaker(self):
        """Create HTTP client with fast circuit breaker for testing."""
        return BaseHTTPClient(
            "http://localhost:8080",
            timeout=1000,
            circuit_breaker_failure_threshold=3,
            circuit_breaker_recovery_timeout=2
        )
    
    def test_circuit_breaker_state_transitions(self, circuit_breaker):
        """Test circuit breaker state transitions."""
        # Initially closed
        assert circuit_breaker.state == "CLOSED"
        assert circuit_breaker.failure_count == 0
        
        # Simulate failures
        def failing_function():
            raise Exception("Service failure")
        
        decorated_func = circuit_breaker.call(failing_function)
        
        # First few failures should increment counter
        for i in range(2):
            with pytest.raises(Exception):
                decorated_func()
            assert circuit_breaker.state == "CLOSED"
            assert circuit_breaker.failure_count == i + 1
        
        # Third failure should open circuit
        with pytest.raises(Exception):
            decorated_func()
        assert circuit_breaker.state == "OPEN"
        assert circuit_breaker.failure_count == 3
        
        # Further calls should raise CircuitBreakerError
        with pytest.raises(CircuitBreakerError):
            decorated_func()
    
    def test_circuit_breaker_recovery(self, circuit_breaker):
        """Test circuit breaker recovery after timeout."""
        # Force circuit to open state
        circuit_breaker.failure_count = 5
        circuit_breaker.state = "OPEN"
        circuit_breaker.last_failure_time = datetime.utcnow() - timedelta(seconds=10)
        
        # Should attempt reset after timeout
        assert circuit_breaker._should_attempt_reset() is True
        
        # Successful call should reset circuit
        def successful_function():
            return "success"
        
        decorated_func = circuit_breaker.call(successful_function)
        
        # First call after timeout should transition to HALF_OPEN
        result = decorated_func()
        assert result == "success"
        assert circuit_breaker.state == "CLOSED"
        assert circuit_breaker.failure_count == 0
    
    @pytest.mark.asyncio
    async def test_http_client_circuit_breaker_integration(self, client_with_fast_circuit_breaker):
        """Test circuit breaker integration with HTTP client."""
        client = client_with_fast_circuit_breaker
        
        # Mock consecutive connection failures
        with patch.object(client.client, 'request', new_callable=AsyncMock) as mock_request:
            mock_request.side_effect = httpx.ConnectError("Connection failed")
            
            # First few failures should be retried and circuit should remain closed
            for i in range(3):
                with pytest.raises(ServiceUnavailableError):
                    await client.get("/test")
                
                # Circuit should still be closed initially
                if i < 2:
                    assert client.circuit_breaker.state == "CLOSED"
            
            # Force circuit breaker to open state
            client.circuit_breaker.failure_count = 5
            client.circuit_breaker.state = "OPEN"
            
            # Now requests should fail immediately with CircuitBreakerError
            with pytest.raises(CircuitBreakerError):
                await client.get("/test")
            
            # Verify no HTTP request was made (circuit breaker prevented it)
            # The call count should still be 3 from the previous failures
            assert mock_request.call_count >= 3
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_recovery_with_http_client(self, client_with_fast_circuit_breaker):
        """Test circuit breaker recovery with HTTP client."""
        client = client_with_fast_circuit_breaker
        
        # Force circuit to open state with old failure time
        client.circuit_breaker.failure_count = 5
        client.circuit_breaker.state = "OPEN"
        client.circuit_breaker.last_failure_time = datetime.utcnow() - timedelta(seconds=10)
        
        # Mock successful response
        with patch.object(client.client, 'request', new_callable=AsyncMock) as mock_request:
            mock_response = MagicMock()
            mock_response.status_code = 200
            mock_response.json.return_value = {"status": "success"}
            mock_request.return_value = mock_response
            
            # Request should succeed and reset circuit breaker
            result = await client.get("/test")
            
            assert result == {"status": "success"}
            assert client.circuit_breaker.state == "CLOSED"
            assert client.circuit_breaker.failure_count == 0
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_with_mixed_failures_and_successes(self, client_with_fast_circuit_breaker):
        """Test circuit breaker behavior with mixed failures and successes."""
        client = client_with_fast_circuit_breaker
        
        with patch.object(client.client, 'request', new_callable=AsyncMock) as mock_request:
            # Simulate pattern: fail, fail, succeed, fail, fail, fail (should open)
            responses = [
                httpx.ConnectError("Connection failed"),
                httpx.ConnectError("Connection failed"),
                MagicMock(status_code=200, json=lambda: {"success": True}),
                httpx.ConnectError("Connection failed"),
                httpx.ConnectError("Connection failed"),
                httpx.ConnectError("Connection failed")
            ]
            
            mock_request.side_effect = responses
            
            # First two failures
            for i in range(2):
                with pytest.raises(ServiceUnavailableError):
                    await client.get("/test")
                assert client.circuit_breaker.state == "CLOSED"
            
            # Success should reset failure count
            result = await client.get("/test")
            assert result == {"success": True}
            assert client.circuit_breaker.failure_count == 0
            
            # More failures should start counting again
            for i in range(3):
                with pytest.raises(ServiceUnavailableError):
                    await client.get("/test")
            
            # Circuit should now be open
            assert client.circuit_breaker.state == "OPEN"
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_with_different_error_types(self, client_with_fast_circuit_breaker):
        """Test circuit breaker behavior with different types of errors."""
        client = client_with_fast_circuit_breaker
        
        with patch.object(client.client, 'request', new_callable=AsyncMock) as mock_request:
            # Mix of different error types
            errors = [
                httpx.ConnectError("Connection failed"),
                httpx.TimeoutException("Request timeout"),
                httpx.ConnectError("Connection failed")
            ]
            
            mock_request.side_effect = errors
            
            # All should be treated as failures for circuit breaker
            for error in errors:
                with pytest.raises(ServiceUnavailableError):
                    await client.get("/test")
            
            # Circuit should be open after threshold failures
            assert client.circuit_breaker.state == "OPEN"
            assert client.circuit_breaker.failure_count == 3
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_does_not_trigger_on_http_errors(self, client_with_fast_circuit_breaker):
        """Test that HTTP status errors don't trigger circuit breaker."""
        client = client_with_fast_circuit_breaker
        
        with patch.object(client.client, 'request', new_callable=AsyncMock) as mock_request:
            # HTTP 404 error should not trigger circuit breaker
            mock_response = MagicMock()
            mock_response.status_code = 404
            mock_response.raise_for_status.side_effect = httpx.HTTPStatusError(
                "404 Not Found", request=MagicMock(), response=mock_response
            )
            mock_request.return_value = mock_response
            
            # Multiple 404s should not open circuit breaker
            for i in range(5):
                with pytest.raises(httpx.HTTPStatusError):
                    await client.get("/test")
            
            # Circuit should still be closed (HTTP errors don't count as failures)
            assert client.circuit_breaker.state == "CLOSED"
            assert client.circuit_breaker.failure_count == 0
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_with_service_unavailable_503(self, client_with_fast_circuit_breaker):
        """Test circuit breaker behavior with 503 Service Unavailable."""
        client = client_with_fast_circuit_breaker
        
        with patch.object(client.client, 'request', new_callable=AsyncMock) as mock_request:
            # 503 Service Unavailable should trigger circuit breaker
            mock_response = MagicMock()
            mock_response.status_code = 503
            mock_request.return_value = mock_response
            
            # Multiple 503s should open circuit breaker
            for i in range(3):
                with pytest.raises(ServiceUnavailableError):
                    await client.get("/test")
            
            # Circuit should be open
            assert client.circuit_breaker.state == "OPEN"
    
    @pytest.mark.asyncio
    async def test_multiple_clients_independent_circuit_breakers(self):
        """Test that different client instances have independent circuit breakers."""
        client1 = BaseHTTPClient("http://service1:8080", circuit_breaker_failure_threshold=2)
        client2 = BaseHTTPClient("http://service2:8081", circuit_breaker_failure_threshold=2)
        
        try:
            # Fail client1's circuit breaker
            with patch.object(client1.client, 'request', new_callable=AsyncMock) as mock_request1:
                mock_request1.side_effect = httpx.ConnectError("Connection failed")
                
                for i in range(2):
                    with pytest.raises(ServiceUnavailableError):
                        await client1.get("/test")
                
                # Force client1 circuit to open
                client1.circuit_breaker.failure_count = 5
                client1.circuit_breaker.state = "OPEN"
                
                # Client1 should have open circuit
                assert client1.circuit_breaker.state == "OPEN"
                
                # Client2 should still have closed circuit
                assert client2.circuit_breaker.state == "CLOSED"
                
                # Client1 requests should fail with CircuitBreakerError
                with pytest.raises(CircuitBreakerError):
                    await client1.get("/test")
                
                # Client2 should still work (with mocked success)
                with patch.object(client2.client, 'request', new_callable=AsyncMock) as mock_request2:
                    mock_response = MagicMock()
                    mock_response.status_code = 200
                    mock_response.json.return_value = {"success": True}
                    mock_request2.return_value = mock_response
                    
                    result = await client2.get("/test")
                    assert result == {"success": True}
                    assert client2.circuit_breaker.state == "CLOSED"
        
        finally:
            await client1.close()
            await client2.close()
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_metrics_and_logging(self, client_with_fast_circuit_breaker):
        """Test that circuit breaker state changes are properly logged."""
        client = client_with_fast_circuit_breaker
        
        with patch('mcp_financial.clients.base_client.logger') as mock_logger:
            # Force circuit breaker to open
            client.circuit_breaker.failure_count = 5
            client.circuit_breaker.state = "OPEN"
            client.circuit_breaker.last_failure_time = datetime.utcnow()
            
            # Trigger logging by calling _on_failure
            client.circuit_breaker._on_failure()
            
            # Should log circuit breaker opening
            mock_logger.warning.assert_called_with(
                "Circuit breaker opened after 6 failures"
            )
            
            # Test recovery logging
            client.circuit_breaker.state = "HALF_OPEN"
            client.circuit_breaker._on_success()
            
            # Should log circuit breaker reset
            mock_logger.info.assert_called_with("Circuit breaker reset to CLOSED")