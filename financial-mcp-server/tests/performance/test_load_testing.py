"""
Performance and load testing for MCP Financial Server.
"""

import pytest
import asyncio
import time
import statistics
from unittest.mock import AsyncMock, MagicMock, patch
from concurrent.futures import ThreadPoolExecutor
from typing import List, Dict, Any

from mcp_financial.server import FinancialMCPServer
from mcp_financial.clients.account_client import AccountServiceClient
from mcp_financial.clients.transaction_client import TransactionServiceClient


class TestPerformanceMetrics:
    """Test suite for performance metrics and benchmarks."""
    
    @pytest.fixture
    def performance_server(self):
        """Create server optimized for performance testing."""
        with patch('mcp_financial.server.Settings') as mock_settings_class:
            mock_settings = MagicMock()
            mock_settings.account_service_url = "http://localhost:8080"
            mock_settings.transaction_service_url = "http://localhost:8081"
            mock_settings.jwt_secret = "test-secret"
            mock_settings.server_timeout = 1000  # Shorter timeout for performance tests
            mock_settings_class.return_value = mock_settings
            
            server = FinancialMCPServer()
            return server
    
    @pytest.mark.asyncio
    async def test_single_request_latency(self, performance_server):
        """Test latency of single MCP tool requests."""
        auth_token = "Bearer perf.test.token"
        
        # Measure account retrieval latency
        start_time = time.perf_counter()
        
        with patch.object(performance_server.account_tools, 'get_account', new_callable=AsyncMock) as mock_get:
            mock_get.return_value = [{"type": "text", "text": '{"success": true}'}]
            
            result = await performance_server.account_tools.get_account("acc_123", auth_token)
            
        end_time = time.perf_counter()
        latency = (end_time - start_time) * 1000  # Convert to milliseconds
        
        # Assert reasonable latency (should be under 100ms for mocked operations)
        assert latency < 100, f"Single request latency too high: {latency}ms"
        assert result is not None
    
    @pytest.mark.asyncio
    async def test_concurrent_request_throughput(self, performance_server):
        """Test throughput under concurrent requests."""
        auth_token = "Bearer perf.test.token"
        num_concurrent_requests = 50
        
        async def make_request():
            """Make a single MCP tool request."""
            with patch.object(performance_server.account_tools, 'get_account', new_callable=AsyncMock) as mock_get:
                mock_get.return_value = [{"type": "text", "text": '{"success": true}'}]
                return await performance_server.account_tools.get_account("acc_123", auth_token)
        
        # Measure concurrent throughput
        start_time = time.perf_counter()
        
        tasks = [make_request() for _ in range(num_concurrent_requests)]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        end_time = time.perf_counter()
        total_time = end_time - start_time
        
        # Calculate throughput
        successful_requests = sum(1 for r in results if not isinstance(r, Exception))
        throughput = successful_requests / total_time
        
        # Assert reasonable throughput (should handle at least 100 requests/second)
        assert throughput > 100, f"Throughput too low: {throughput} requests/second"
        assert successful_requests == num_concurrent_requests, f"Some requests failed: {len(results) - successful_requests}"
    
    @pytest.mark.asyncio
    async def test_memory_usage_under_load(self, performance_server):
        """Test memory usage under sustained load."""
        import psutil
        import os
        
        process = psutil.Process(os.getpid())
        initial_memory = process.memory_info().rss / 1024 / 1024  # MB
        
        auth_token = "Bearer perf.test.token"
        
        async def sustained_load():
            """Generate sustained load for memory testing."""
            tasks = []
            for i in range(100):
                with patch.object(performance_server.transaction_tools, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                    mock_deposit.return_value = [{"type": "text", "text": '{"success": true}'}]
                    task = performance_server.transaction_tools.deposit_funds(
                        f"acc_{i}", 100.0, f"Load test {i}", auth_token
                    )
                    tasks.append(task)
            
            await asyncio.gather(*tasks, return_exceptions=True)
        
        # Run sustained load
        for _ in range(5):  # 5 rounds of 100 requests each
            await sustained_load()
            await asyncio.sleep(0.1)  # Brief pause between rounds
        
        final_memory = process.memory_info().rss / 1024 / 1024  # MB
        memory_increase = final_memory - initial_memory
        
        # Assert reasonable memory usage (should not increase by more than 50MB)
        assert memory_increase < 50, f"Memory usage increased too much: {memory_increase}MB"
    
    @pytest.mark.asyncio
    async def test_response_time_distribution(self, performance_server):
        """Test response time distribution under load."""
        auth_token = "Bearer perf.test.token"
        response_times = []
        
        async def timed_request():
            """Make a timed request and record response time."""
            start = time.perf_counter()
            
            with patch.object(performance_server.query_tools, 'get_transaction_history', new_callable=AsyncMock) as mock_history:
                mock_history.return_value = [{"type": "text", "text": '{"success": true}'}]
                await performance_server.query_tools.get_transaction_history(
                    "acc_123", 0, 20, None, None, auth_token
                )
            
            end = time.perf_counter()
            return (end - start) * 1000  # Convert to milliseconds
        
        # Collect response times
        tasks = [timed_request() for _ in range(100)]
        response_times = await asyncio.gather(*tasks)
        
        # Calculate statistics
        mean_time = statistics.mean(response_times)
        median_time = statistics.median(response_times)
        p95_time = sorted(response_times)[int(0.95 * len(response_times))]
        p99_time = sorted(response_times)[int(0.99 * len(response_times))]
        
        # Assert reasonable response time distribution
        assert mean_time < 50, f"Mean response time too high: {mean_time}ms"
        assert median_time < 30, f"Median response time too high: {median_time}ms"
        assert p95_time < 100, f"95th percentile too high: {p95_time}ms"
        assert p99_time < 200, f"99th percentile too high: {p99_time}ms"
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_performance_impact(self, performance_server):
        """Test performance impact of circuit breaker operations."""
        auth_token = "Bearer perf.test.token"
        
        # Test normal operation performance
        async def normal_request():
            with patch.object(performance_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                mock_get.return_value = {"id": "acc_123", "balance": 1000.0}
                start = time.perf_counter()
                await performance_server.account_client.get_account("acc_123", auth_token)
                return time.perf_counter() - start
        
        normal_times = []
        for _ in range(50):
            normal_times.append(await normal_request())
        
        normal_avg = statistics.mean(normal_times) * 1000  # Convert to ms
        
        # Test circuit breaker open performance
        async def circuit_breaker_request():
            with patch.object(performance_server.account_client.circuit_breaker, 'state', 'OPEN'):
                start = time.perf_counter()
                try:
                    await performance_server.account_client.get_account("acc_123", auth_token)
                except:
                    pass  # Expected to fail fast
                return time.perf_counter() - start
        
        cb_times = []
        for _ in range(50):
            cb_times.append(await circuit_breaker_request())
        
        cb_avg = statistics.mean(cb_times) * 1000  # Convert to ms
        
        # Circuit breaker should fail fast (much faster than normal requests)
        assert cb_avg < normal_avg / 2, f"Circuit breaker not failing fast enough: {cb_avg}ms vs {normal_avg}ms"


class TestLoadTesting:
    """Load testing scenarios for MCP Financial Server."""
    
    @pytest.fixture
    async def load_test_server(self):
        """Create server for load testing."""
        with patch('mcp_financial.server.Settings') as mock_settings_class:
            mock_settings = MagicMock()
            mock_settings.account_service_url = "http://localhost:8080"
            mock_settings.transaction_service_url = "http://localhost:8081"
            mock_settings.jwt_secret = "test-secret"
            mock_settings_class.return_value = mock_settings
            
            server = FinancialMCPServer()
            
            # Mock service responses for load testing
            with patch.object(server.account_client, 'create_account', new_callable=AsyncMock) as mock_create, \
                 patch.object(server.transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit, \
                 patch.object(server.auth_handler, 'extract_user_context') as mock_auth:
                
                mock_auth.return_value = MagicMock(
                    user_id="load_user",
                    roles=["customer"],
                    permissions=["account:create", "transaction:create"]
                )
                
                mock_create.return_value = {"id": "acc_load_123", "status": "ACTIVE"}
                mock_deposit.return_value = {"id": "txn_load_456", "status": "COMPLETED"}
                
                yield server
    
    @pytest.mark.asyncio
    async def test_sustained_load_scenario(self, load_test_server):
        """Test server under sustained load."""
        auth_token = "Bearer load.test.token"
        duration_seconds = 10
        requests_per_second = 20
        
        start_time = time.time()
        total_requests = 0
        successful_requests = 0
        errors = []
        
        async def load_generator():
            """Generate sustained load."""
            nonlocal total_requests, successful_requests
            
            while time.time() - start_time < duration_seconds:
                try:
                    # Mix of different operations
                    operations = [
                        lambda: performance_server.account_tools.create_account(
                            f"user_{total_requests}", "CHECKING", 0.0, auth_token
                        ),
                        lambda: performance_server.transaction_tools.deposit_funds(
                            "acc_123", 100.0, f"Load test {total_requests}", auth_token
                        )
                    ]
                    
                    operation = operations[total_requests % len(operations)]
                    
                    with patch.object(load_test_server.account_tools, 'create_account', new_callable=AsyncMock) as mock_create, \
                         patch.object(load_test_server.transaction_tools, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                        
                        mock_create.return_value = [{"type": "text", "text": '{"success": true}'}]
                        mock_deposit.return_value = [{"type": "text", "text": '{"success": true}'}]
                        
                        await operation()
                        successful_requests += 1
                    
                except Exception as e:
                    errors.append(str(e))
                
                total_requests += 1
                await asyncio.sleep(1.0 / requests_per_second)
        
        # Run load test
        await load_generator()
        
        # Calculate metrics
        actual_duration = time.time() - start_time
        actual_rps = total_requests / actual_duration
        success_rate = successful_requests / total_requests if total_requests > 0 else 0
        
        # Assert load test results
        assert actual_rps >= requests_per_second * 0.8, f"RPS too low: {actual_rps}"
        assert success_rate >= 0.95, f"Success rate too low: {success_rate}"
        assert len(errors) < total_requests * 0.05, f"Too many errors: {len(errors)}"
    
    @pytest.mark.asyncio
    async def test_spike_load_scenario(self, load_test_server):
        """Test server response to sudden load spikes."""
        auth_token = "Bearer spike.test.token"
        
        # Normal load phase
        async def normal_load():
            tasks = []
            for i in range(10):
                with patch.object(load_test_server.account_tools, 'get_account', new_callable=AsyncMock) as mock_get:
                    mock_get.return_value = [{"type": "text", "text": '{"success": true}'}]
                    task = load_test_server.account_tools.get_account(f"acc_{i}", auth_token)
                    tasks.append(task)
            
            return await asyncio.gather(*tasks, return_exceptions=True)
        
        # Spike load phase
        async def spike_load():
            tasks = []
            for i in range(100):  # 10x increase
                with patch.object(load_test_server.transaction_tools, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                    mock_deposit.return_value = [{"type": "text", "text": '{"success": true}'}]
                    task = load_test_server.transaction_tools.deposit_funds(
                        f"acc_{i}", 100.0, f"Spike test {i}", auth_token
                    )
                    tasks.append(task)
            
            return await asyncio.gather(*tasks, return_exceptions=True)
        
        # Execute normal load
        normal_start = time.perf_counter()
        normal_results = await normal_load()
        normal_duration = time.perf_counter() - normal_start
        
        # Execute spike load
        spike_start = time.perf_counter()
        spike_results = await spike_load()
        spike_duration = time.perf_counter() - spike_start
        
        # Analyze results
        normal_success_rate = sum(1 for r in normal_results if not isinstance(r, Exception)) / len(normal_results)
        spike_success_rate = sum(1 for r in spike_results if not isinstance(r, Exception)) / len(spike_results)
        
        # Server should handle spike gracefully
        assert normal_success_rate >= 0.95, f"Normal load success rate too low: {normal_success_rate}"
        assert spike_success_rate >= 0.80, f"Spike load success rate too low: {spike_success_rate}"
        assert spike_duration < normal_duration * 20, f"Spike response time too slow: {spike_duration}s"
    
    @pytest.mark.asyncio
    async def test_resource_exhaustion_scenario(self, load_test_server):
        """Test server behavior under resource exhaustion."""
        auth_token = "Bearer resource.test.token"
        
        # Create many concurrent long-running operations
        async def resource_intensive_operation():
            """Simulate resource-intensive operation."""
            with patch.object(load_test_server.query_tools, 'get_transaction_history', new_callable=AsyncMock) as mock_history:
                # Simulate processing delay
                await asyncio.sleep(0.1)
                mock_history.return_value = [{"type": "text", "text": '{"success": true}'}]
                return await load_test_server.query_tools.get_transaction_history(
                    "acc_123", 0, 1000, None, None, auth_token
                )
        
        # Launch many concurrent operations
        num_operations = 200
        start_time = time.perf_counter()
        
        tasks = [resource_intensive_operation() for _ in range(num_operations)]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        end_time = time.perf_counter()
        total_duration = end_time - start_time
        
        # Analyze resource exhaustion handling
        successful_operations = sum(1 for r in results if not isinstance(r, Exception))
        failed_operations = num_operations - successful_operations
        
        # Server should handle resource exhaustion gracefully
        assert successful_operations > 0, "No operations succeeded under resource pressure"
        assert total_duration < 60, f"Operations took too long: {total_duration}s"
        
        # If some operations fail, they should fail fast
        if failed_operations > 0:
            assert failed_operations < num_operations * 0.5, f"Too many operations failed: {failed_operations}"
    
    @pytest.mark.asyncio
    async def test_connection_pool_exhaustion(self, load_test_server):
        """Test behavior when HTTP connection pools are exhausted."""
        auth_token = "Bearer pool.test.token"
        
        # Mock connection pool limits
        with patch.object(load_test_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
            # Simulate connection pool exhaustion after some requests
            call_count = 0
            
            async def connection_limited_get(*args, **kwargs):
                nonlocal call_count
                call_count += 1
                if call_count > 50:  # Simulate pool exhaustion
                    raise Exception("Connection pool exhausted")
                return {"id": "acc_123", "balance": 1000.0}
            
            mock_get.side_effect = connection_limited_get
            
            # Make many concurrent requests
            tasks = []
            for i in range(100):
                task = load_test_server.account_client.get_account(f"acc_{i}", auth_token)
                tasks.append(task)
            
            results = await asyncio.gather(*tasks, return_exceptions=True)
            
            # Analyze connection pool handling
            successful_requests = sum(1 for r in results if not isinstance(r, Exception))
            failed_requests = len(results) - successful_requests
            
            # Should handle some requests successfully before pool exhaustion
            assert successful_requests >= 50, f"Too few successful requests: {successful_requests}"
            assert failed_requests > 0, "Expected some failures due to pool exhaustion"


class TestStressScenarios:
    """Stress testing scenarios for edge cases."""
    
    @pytest.mark.asyncio
    async def test_large_payload_handling(self):
        """Test handling of large request/response payloads."""
        # Create large transaction history response
        large_history = {
            "content": [
                {
                    "id": f"txn_{i}",
                    "amount": 100.0,
                    "description": "A" * 1000,  # Large description
                    "metadata": {"key": "B" * 500}  # Large metadata
                }
                for i in range(1000)  # 1000 transactions
            ],
            "totalElements": 1000
        }
        
        # Test serialization/deserialization performance
        import json
        
        start_time = time.perf_counter()
        serialized = json.dumps(large_history)
        deserialized = json.loads(serialized)
        end_time = time.perf_counter()
        
        processing_time = (end_time - start_time) * 1000  # Convert to ms
        
        # Should handle large payloads efficiently
        assert processing_time < 1000, f"Large payload processing too slow: {processing_time}ms"
        assert len(deserialized["content"]) == 1000
        assert len(serialized) > 1000000  # Ensure it's actually large
    
    @pytest.mark.asyncio
    async def test_rapid_authentication_requests(self):
        """Test rapid authentication token validation."""
        from mcp_financial.auth.jwt_handler import JWTAuthHandler
        
        jwt_handler = JWTAuthHandler("test-secret")
        
        # Create many authentication requests
        tokens = [f"Bearer test.token.{i}" for i in range(1000)]
        
        async def validate_token(token):
            try:
                return jwt_handler.validate_token(token.replace("Bearer ", ""))
            except:
                return None
        
        start_time = time.perf_counter()
        
        tasks = [validate_token(token) for token in tokens]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        end_time = time.perf_counter()
        total_time = end_time - start_time
        
        # Should handle rapid auth requests efficiently
        validation_rate = len(tokens) / total_time
        assert validation_rate > 1000, f"Token validation rate too low: {validation_rate} tokens/second"
    
    @pytest.mark.asyncio
    async def test_error_rate_under_stress(self):
        """Test error handling under high stress conditions."""
        # Simulate various error conditions under load
        error_scenarios = [
            Exception("Database connection failed"),
            TimeoutError("Request timeout"),
            ValueError("Invalid input data"),
            ConnectionError("Service unavailable")
        ]
        
        results = []
        
        async def error_prone_operation(error_type):
            try:
                # Simulate some processing
                await asyncio.sleep(0.01)
                raise error_type
            except Exception as e:
                return {"error": str(e), "type": type(e).__name__}
        
        # Generate high error load
        tasks = []
        for _ in range(100):
            for error in error_scenarios:
                tasks.append(error_prone_operation(error))
        
        start_time = time.perf_counter()
        results = await asyncio.gather(*tasks, return_exceptions=True)
        end_time = time.perf_counter()
        
        processing_time = end_time - start_time
        
        # Should handle errors efficiently without crashing
        assert len(results) == 400  # 100 * 4 error types
        assert processing_time < 10, f"Error handling too slow: {processing_time}s"
        
        # Verify error distribution
        error_types = {}
        for result in results:
            if isinstance(result, dict) and "type" in result:
                error_type = result["type"]
                error_types[error_type] = error_types.get(error_type, 0) + 1
        
        assert len(error_types) == 4, "Not all error types were handled"