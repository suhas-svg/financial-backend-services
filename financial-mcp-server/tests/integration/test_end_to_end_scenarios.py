"""
End-to-end integration tests with real backend services.
"""

import pytest
import asyncio
import json
from unittest.mock import AsyncMock, MagicMock, patch
from decimal import Decimal
from datetime import datetime, timedelta

from mcp_financial.server import FinancialMCPServer
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext


class TestEndToEndScenarios:
    """End-to-end integration test scenarios."""
    
    @pytest.fixture
    async def e2e_server(self):
        """Create server for end-to-end testing."""
        with patch('mcp_financial.server.Settings') as mock_settings_class:
            mock_settings = MagicMock()
            mock_settings.account_service_url = "http://localhost:8080"
            mock_settings.transaction_service_url = "http://localhost:8081"
            mock_settings.jwt_secret = "test-secret-key"
            mock_settings.server_timeout = 5000
            mock_settings_class.return_value = mock_settings
            
            server = FinancialMCPServer()
            yield server
    
    @pytest.fixture
    def customer_user_context(self):
        """Customer user context for testing."""
        return UserContext(
            user_id="customer_123",
            username="john_doe",
            roles=["customer"],
            permissions=[
                "account:read", "account:create", "account:update",
                "transaction:create", "transaction:read"
            ]
        )
    
    @pytest.fixture
    def financial_officer_context(self):
        """Financial officer user context for testing."""
        return UserContext(
            user_id="officer_456",
            username="jane_smith",
            roles=["financial_officer"],
            permissions=[
                "account:read", "account:create", "account:update", "account:delete",
                "transaction:create", "transaction:read", "transaction:reverse",
                "account:balance:update"
            ]
        )
    
    @pytest.mark.asyncio
    async def test_complete_account_lifecycle(self, e2e_server, customer_user_context):
        """Test complete account lifecycle from creation to closure."""
        auth_token = "Bearer customer.jwt.token"
        
        # Mock authentication
        with patch.object(e2e_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = customer_user_context
            
            # Step 1: Create account
            with patch.object(e2e_server.account_client, 'create_account', new_callable=AsyncMock) as mock_create:
                mock_create.return_value = {
                    "id": "acc_lifecycle_123",
                    "ownerId": "customer_123",
                    "accountType": "CHECKING",
                    "balance": 0.0,
                    "status": "ACTIVE",
                    "createdAt": "2024-01-01T10:00:00Z"
                }
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
                    create_result = await e2e_server.account_tools.create_account(
                        "customer_123", "CHECKING", 0.0, auth_token
                    )
                
                # Verify account creation
                create_data = json.loads(create_result[0].text)
                assert create_data["success"] is True
                account_id = create_data["data"]["id"]
                assert account_id == "acc_lifecycle_123"
            
            # Step 2: Make initial deposit
            with patch.object(e2e_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get_account, \
                 patch.object(e2e_server.transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                
                mock_get_account.return_value = {
                    "id": account_id,
                    "ownerId": "customer_123",
                    "status": "ACTIVE"
                }
                
                mock_deposit.return_value = {
                    "id": "txn_deposit_123",
                    "accountId": account_id,
                    "amount": 1000.0,
                    "transactionType": "DEPOSIT",
                    "status": "COMPLETED"
                }
                
                with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                    deposit_result = await e2e_server.transaction_tools.deposit_funds(
                        account_id, 1000.0, "Initial deposit", auth_token
                    )
                
                # Verify deposit
                deposit_data = json.loads(deposit_result[0].text)
                assert deposit_data["success"] is True
                assert deposit_data["data"]["amount"] == 1000.0
            
            # Step 3: Check account balance
            with patch.object(e2e_server.account_client, 'get_account_balance', new_callable=AsyncMock) as mock_balance:
                mock_balance.return_value = {
                    "accountId": account_id,
                    "balance": 1000.0,
                    "availableBalance": 1000.0,
                    "lastUpdated": "2024-01-01T10:30:00Z"
                }
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                    balance_result = await e2e_server.account_tools.get_account_balance(
                        account_id, auth_token
                    )
                
                # Verify balance
                balance_data = json.loads(balance_result[0].text)
                assert balance_data["success"] is True
                assert balance_data["data"]["balance"] == 1000.0
            
            # Step 4: Make withdrawal
            with patch.object(e2e_server.account_client, 'get_account_balance', new_callable=AsyncMock) as mock_balance2, \
                 patch.object(e2e_server.transaction_client, 'withdraw_funds', new_callable=AsyncMock) as mock_withdraw:
                
                mock_balance2.return_value = {
                    "accountId": account_id,
                    "balance": 1000.0,
                    "availableBalance": 1000.0
                }
                
                mock_withdraw.return_value = {
                    "id": "txn_withdraw_123",
                    "accountId": account_id,
                    "amount": -200.0,
                    "transactionType": "WITHDRAWAL",
                    "status": "COMPLETED"
                }
                
                with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                    withdraw_result = await e2e_server.transaction_tools.withdraw_funds(
                        account_id, 200.0, "ATM withdrawal", auth_token
                    )
                
                # Verify withdrawal
                withdraw_data = json.loads(withdraw_result[0].text)
                assert withdraw_data["success"] is True
                assert withdraw_data["data"]["amount"] == -200.0
            
            # Step 5: Get transaction history
            with patch.object(e2e_server.transaction_client, 'get_transaction_history', new_callable=AsyncMock) as mock_history:
                mock_history.return_value = {
                    "content": [
                        {
                            "id": "txn_deposit_123",
                            "accountId": account_id,
                            "amount": 1000.0,
                            "transactionType": "DEPOSIT",
                            "createdAt": "2024-01-01T10:00:00Z"
                        },
                        {
                            "id": "txn_withdraw_123",
                            "accountId": account_id,
                            "amount": -200.0,
                            "transactionType": "WITHDRAWAL",
                            "createdAt": "2024-01-01T10:30:00Z"
                        }
                    ],
                    "totalElements": 2
                }
                
                with patch('mcp_financial.tools.query_tools.PermissionChecker.can_access_account', return_value=True):
                    history_result = await e2e_server.query_tools.get_transaction_history(
                        account_id, 0, 20, None, None, auth_token
                    )
                
                # Verify transaction history
                history_data = json.loads(history_result[0].text)
                assert history_data["success"] is True
                assert len(history_data["data"]["content"]) == 2
    
    @pytest.mark.asyncio
    async def test_multi_account_transfer_scenario(self, e2e_server, customer_user_context):
        """Test transfer between multiple accounts."""
        auth_token = "Bearer customer.jwt.token"
        
        with patch.object(e2e_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = customer_user_context
            
            # Setup: Two accounts exist
            source_account = "acc_source_123"
            dest_account = "acc_dest_456"
            
            with patch.object(e2e_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get_account, \
                 patch.object(e2e_server.account_client, 'get_account_balance', new_callable=AsyncMock) as mock_balance, \
                 patch.object(e2e_server.transaction_client, 'transfer_funds', new_callable=AsyncMock) as mock_transfer:
                
                # Mock account details
                mock_get_account.side_effect = [
                    {"id": source_account, "ownerId": "customer_123", "status": "ACTIVE"},
                    {"id": dest_account, "ownerId": "customer_456", "status": "ACTIVE"}
                ]
                
                # Mock sufficient balance
                mock_balance.return_value = {
                    "accountId": source_account,
                    "balance": 1500.0,
                    "availableBalance": 1500.0
                }
                
                # Mock successful transfer
                mock_transfer.return_value = {
                    "id": "txn_transfer_789",
                    "fromAccountId": source_account,
                    "toAccountId": dest_account,
                    "amount": 500.0,
                    "transactionType": "TRANSFER",
                    "status": "COMPLETED",
                    "description": "Transfer to friend"
                }
                
                with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                    transfer_result = await e2e_server.transaction_tools.transfer_funds(
                        source_account, dest_account, 500.0, "Transfer to friend", auth_token
                    )
                
                # Verify transfer
                transfer_data = json.loads(transfer_result[0].text)
                assert transfer_data["success"] is True
                assert transfer_data["data"]["amount"] == 500.0
                assert transfer_data["data"]["fromAccountId"] == source_account
                assert transfer_data["data"]["toAccountId"] == dest_account
    
    @pytest.mark.asyncio
    async def test_financial_officer_operations(self, e2e_server, financial_officer_context):
        """Test financial officer privileged operations."""
        auth_token = "Bearer officer.jwt.token"
        
        with patch.object(e2e_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = financial_officer_context
            
            # Test 1: Manual balance adjustment
            account_id = "acc_adjustment_123"
            
            with patch.object(e2e_server.account_client, 'update_account_balance', new_callable=AsyncMock) as mock_update_balance:
                mock_update_balance.return_value = {
                    "accountId": account_id,
                    "balance": 2500.0,
                    "lastUpdated": "2024-01-01T11:00:00Z",
                    "reason": "Manual adjustment - error correction"
                }
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=True):
                    adjustment_result = await e2e_server.account_tools.update_account_balance(
                        account_id, 2500.0, "Manual adjustment - error correction", auth_token
                    )
                
                # Verify balance adjustment
                adjustment_data = json.loads(adjustment_result[0].text)
                assert adjustment_data["success"] is True
                assert adjustment_data["data"]["balance"] == 2500.0
            
            # Test 2: Transaction reversal
            transaction_id = "txn_to_reverse_456"
            
            with patch.object(e2e_server.transaction_client, 'reverse_transaction', new_callable=AsyncMock) as mock_reverse:
                mock_reverse.return_value = {
                    "id": "txn_reversal_789",
                    "originalTransactionId": transaction_id,
                    "amount": -100.0,
                    "transactionType": "REVERSAL",
                    "status": "COMPLETED",
                    "reason": "Customer dispute resolution"
                }
                
                with patch('mcp_financial.tools.transaction_tools.PermissionChecker.has_permission', return_value=True):
                    reversal_result = await e2e_server.transaction_tools.reverse_transaction(
                        transaction_id, "Customer dispute resolution", auth_token
                    )
                
                # Verify transaction reversal
                reversal_data = json.loads(reversal_result[0].text)
                assert reversal_data["success"] is True
                assert reversal_data["data"]["originalTransactionId"] == transaction_id
    
    @pytest.mark.asyncio
    async def test_error_recovery_scenarios(self, e2e_server, customer_user_context):
        """Test error recovery and resilience scenarios."""
        auth_token = "Bearer customer.jwt.token"
        
        with patch.object(e2e_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = customer_user_context
            
            # Scenario 1: Service temporarily unavailable
            with patch.object(e2e_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get:
                # First call fails, second succeeds (retry logic)
                mock_get.side_effect = [
                    Exception("Service temporarily unavailable"),
                    {
                        "id": "acc_recovery_123",
                        "ownerId": "customer_123",
                        "balance": 1000.0,
                        "status": "ACTIVE"
                    }
                ]
                
                with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
                    # Should succeed on retry
                    result = await e2e_server.account_tools.get_account("acc_recovery_123", auth_token)
                
                # Verify recovery
                data = json.loads(result[0].text)
                assert data["success"] is True
                assert data["data"]["id"] == "acc_recovery_123"
            
            # Scenario 2: Network timeout with circuit breaker
            with patch.object(e2e_server.transaction_client, 'get_transaction_history', new_callable=AsyncMock) as mock_history:
                # Simulate timeout
                mock_history.side_effect = asyncio.TimeoutError("Request timeout")
                
                with patch('mcp_financial.tools.query_tools.PermissionChecker.can_access_account', return_value=True):
                    result = await e2e_server.query_tools.get_transaction_history(
                        "acc_123", 0, 20, None, None, auth_token
                    )
                
                # Should return error response
                data = json.loads(result[0].text)
                assert data["success"] is False
                assert "timeout" in data["error_message"].lower()
    
    @pytest.mark.asyncio
    async def test_concurrent_operations_consistency(self, e2e_server, customer_user_context):
        """Test consistency under concurrent operations."""
        auth_token = "Bearer customer.jwt.token"
        account_id = "acc_concurrent_123"
        
        with patch.object(e2e_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = customer_user_context
            
            # Mock account and balance checks
            with patch.object(e2e_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get_account, \
                 patch.object(e2e_server.account_client, 'get_account_balance', new_callable=AsyncMock) as mock_balance, \
                 patch.object(e2e_server.transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                
                mock_get_account.return_value = {
                    "id": account_id,
                    "ownerId": "customer_123",
                    "status": "ACTIVE"
                }
                
                mock_balance.return_value = {
                    "accountId": account_id,
                    "balance": 1000.0,
                    "availableBalance": 1000.0
                }
                
                # Mock successful deposits with unique IDs
                deposit_counter = 0
                
                async def mock_deposit_func(*args, **kwargs):
                    nonlocal deposit_counter
                    deposit_counter += 1
                    return {
                        "id": f"txn_concurrent_{deposit_counter}",
                        "accountId": account_id,
                        "amount": 100.0,
                        "transactionType": "DEPOSIT",
                        "status": "COMPLETED"
                    }
                
                mock_deposit.side_effect = mock_deposit_func
                
                # Execute concurrent deposits
                with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                    tasks = []
                    for i in range(5):
                        task = e2e_server.transaction_tools.deposit_funds(
                            account_id, 100.0, f"Concurrent deposit {i}", auth_token
                        )
                        tasks.append(task)
                    
                    results = await asyncio.gather(*tasks)
                
                # Verify all operations completed successfully
                assert len(results) == 5
                for result in results:
                    data = json.loads(result[0].text)
                    assert data["success"] is True
                    assert data["data"]["amount"] == 100.0
                
                # Verify unique transaction IDs
                transaction_ids = [json.loads(r[0].text)["data"]["id"] for r in results]
                assert len(set(transaction_ids)) == 5, "Transaction IDs should be unique"
    
    @pytest.mark.asyncio
    async def test_audit_trail_completeness(self, e2e_server, financial_officer_context):
        """Test that all operations create proper audit trails."""
        auth_token = "Bearer officer.jwt.token"
        
        with patch.object(e2e_server.auth_handler, 'extract_user_context') as mock_auth:
            mock_auth.return_value = financial_officer_context
            
            # Track audit events
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
            
            # Mock audit logging
            with patch('mcp_financial.utils.logging.log_audit_event', side_effect=capture_audit_event):
                
                # Perform various operations
                operations = [
                    ("create_account", "customer_789", "SAVINGS", 500.0),
                    ("deposit_funds", "acc_audit_123", 250.0, "Audit test deposit"),
                    ("update_account_balance", "acc_audit_123", 1000.0, "Balance correction")
                ]
                
                for operation in operations:
                    if operation[0] == "create_account":
                        with patch.object(e2e_server.account_client, 'create_account', new_callable=AsyncMock) as mock_create:
                            mock_create.return_value = {
                                "id": "acc_audit_123",
                                "ownerId": operation[1],
                                "accountType": operation[2],
                                "balance": operation[3]
                            }
                            
                            with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
                                await e2e_server.account_tools.create_account(
                                    operation[1], operation[2], operation[3], auth_token
                                )
                    
                    elif operation[0] == "deposit_funds":
                        with patch.object(e2e_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get, \
                             patch.object(e2e_server.transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                            
                            mock_get.return_value = {"id": operation[1], "ownerId": "customer_789", "status": "ACTIVE"}
                            mock_deposit.return_value = {
                                "id": "txn_audit_456",
                                "accountId": operation[1],
                                "amount": operation[2],
                                "transactionType": "DEPOSIT"
                            }
                            
                            with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
                                await e2e_server.transaction_tools.deposit_funds(
                                    operation[1], operation[2], operation[3], auth_token
                                )
                    
                    elif operation[0] == "update_account_balance":
                        with patch.object(e2e_server.account_client, 'update_account_balance', new_callable=AsyncMock) as mock_update:
                            mock_update.return_value = {
                                "accountId": operation[1],
                                "balance": operation[2],
                                "reason": operation[3]
                            }
                            
                            with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=True):
                                await e2e_server.account_tools.update_account_balance(
                                    operation[1], operation[2], operation[3], auth_token
                                )
                
                # Verify audit trail completeness
                assert len(audit_events) >= len(operations), "Missing audit events"
                
                # Verify audit event structure
                for event in audit_events:
                    assert "event_type" in event
                    assert "user_id" in event
                    assert "resource_id" in event
                    assert "action" in event
                    assert "timestamp" in event
                    assert event["user_id"] == financial_officer_context.user_id