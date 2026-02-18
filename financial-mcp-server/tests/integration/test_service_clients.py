"""
Integration tests for HTTP service clients.
"""

import pytest
import asyncio
import httpx
import sys
import os
from unittest.mock import AsyncMock, patch, MagicMock
from decimal import Decimal
from datetime import datetime

# Add src directory to Python path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', 'src'))

from mcp_financial.clients.account_client import AccountServiceClient
from mcp_financial.clients.transaction_client import TransactionServiceClient
from mcp_financial.clients.base_client import BaseHTTPClient, CircuitBreakerError, ServiceUnavailableError


class TestAccountServiceClient:
    """Integration tests for Account Service HTTP client."""
    
    @pytest.fixture
    def account_client(self):
        """Create Account Service client for testing."""
        return AccountServiceClient("http://localhost:8080", timeout=5000)
    
    @pytest.fixture
    def auth_token(self):
        """Mock JWT token for testing."""
        return "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.token"
    
    @pytest.mark.asyncio
    async def test_get_account_success(self, account_client, auth_token):
        """Test successful account retrieval."""
        expected_response = {
            "id": "acc_123",
            "ownerId": "user_456",
            "accountType": "CHECKING",
            "balance": 1000.00,
            "status": "ACTIVE"
        }
        
        with patch.object(account_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await account_client.get_account("acc_123", auth_token)
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "GET", "/api/accounts/acc_123", params=None, auth_token=auth_token
            )
    
    @pytest.mark.asyncio
    async def test_create_account_success(self, account_client, auth_token):
        """Test successful account creation."""
        account_data = {
            "ownerId": "user_456",
            "accountType": "SAVINGS",
            "balance": 500.00
        }
        
        expected_response = {
            "id": "acc_789",
            **account_data,
            "status": "ACTIVE",
            "createdAt": "2024-01-01T10:00:00Z"
        }
        
        with patch.object(account_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await account_client.create_account(account_data, auth_token)
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "POST", "/api/accounts", data=account_data, auth_token=auth_token
            )
    
    @pytest.mark.asyncio
    async def test_get_account_balance_success(self, account_client, auth_token):
        """Test successful balance retrieval."""
        expected_response = {
            "accountId": "acc_123",
            "balance": 1500.75,
            "availableBalance": 1500.75,
            "lastUpdated": "2024-01-01T10:00:00Z"
        }
        
        with patch.object(account_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await account_client.get_account_balance("acc_123", auth_token)
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "GET", "/api/accounts/acc_123/balance", params=None, auth_token=auth_token
            )
    
    @pytest.mark.asyncio
    async def test_get_accounts_by_owner_success(self, account_client, auth_token):
        """Test successful retrieval of accounts by owner."""
        expected_response = [
            {
                "id": "acc_123",
                "ownerId": "user_456",
                "accountType": "CHECKING",
                "balance": 1000.00
            },
            {
                "id": "acc_124",
                "ownerId": "user_456",
                "accountType": "SAVINGS",
                "balance": 2000.00
            }
        ]
        
        with patch.object(account_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await account_client.get_accounts_by_owner("user_456", auth_token)
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "GET", "/api/accounts", params={"ownerId": "user_456"}, auth_token=auth_token
            )
    
    @pytest.mark.asyncio
    async def test_get_accounts_by_owner_paginated_response(self, account_client, auth_token):
        """Test handling of paginated response for accounts by owner."""
        paginated_response = {
            "content": [
                {"id": "acc_123", "ownerId": "user_456", "accountType": "CHECKING"},
                {"id": "acc_124", "ownerId": "user_456", "accountType": "SAVINGS"}
            ],
            "totalElements": 2,
            "totalPages": 1,
            "number": 0
        }
        
        with patch.object(account_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = paginated_response
            
            result = await account_client.get_accounts_by_owner("user_456", auth_token)
            
            assert result == paginated_response["content"]
    
    @pytest.mark.asyncio
    async def test_search_accounts_success(self, account_client, auth_token):
        """Test successful account search."""
        search_params = {
            "accountType": "CHECKING",
            "status": "ACTIVE",
            "page": 0,
            "size": 10
        }
        
        expected_response = {
            "content": [
                {"id": "acc_123", "accountType": "CHECKING", "status": "ACTIVE"}
            ],
            "totalElements": 1,
            "totalPages": 1
        }
        
        with patch.object(account_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await account_client.search_accounts(search_params, auth_token)
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "GET", "/api/accounts/search", params=search_params, auth_token=auth_token
            )
    
    @pytest.mark.asyncio
    async def test_account_client_error_handling(self, account_client, auth_token):
        """Test error handling in account client."""
        with patch.object(account_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.side_effect = httpx.HTTPStatusError(
                "404 Not Found", 
                request=MagicMock(), 
                response=MagicMock(status_code=404)
            )
            
            with pytest.raises(httpx.HTTPStatusError):
                await account_client.get_account("nonexistent", auth_token)


class TestTransactionServiceClient:
    """Integration tests for Transaction Service HTTP client."""
    
    @pytest.fixture
    def transaction_client(self):
        """Create Transaction Service client for testing."""
        return TransactionServiceClient("http://localhost:8081", timeout=5000)
    
    @pytest.fixture
    def auth_token(self):
        """Mock JWT token for testing."""
        return "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.token"
    
    @pytest.mark.asyncio
    async def test_create_transaction_success(self, transaction_client, auth_token):
        """Test successful transaction creation."""
        transaction_data = {
            "accountId": "acc_123",
            "amount": 100.00,
            "transactionType": "DEPOSIT",
            "description": "Test deposit"
        }
        
        expected_response = {
            "id": "txn_456",
            **transaction_data,
            "status": "COMPLETED",
            "createdAt": "2024-01-01T10:00:00Z"
        }
        
        with patch.object(transaction_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await transaction_client.create_transaction(transaction_data, auth_token)
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "POST", "/api/transactions", data=transaction_data, auth_token=auth_token
            )
    
    @pytest.mark.asyncio
    async def test_deposit_funds_success(self, transaction_client, auth_token):
        """Test successful deposit operation."""
        expected_response = {
            "id": "txn_789",
            "accountId": "acc_123",
            "amount": 250.00,
            "transactionType": "DEPOSIT",
            "status": "COMPLETED"
        }
        
        with patch.object(transaction_client, 'create_transaction', new_callable=AsyncMock) as mock_create:
            mock_create.return_value = expected_response
            
            result = await transaction_client.deposit_funds(
                "acc_123", Decimal("250.00"), "Test deposit", auth_token
            )
            
            assert result == expected_response
            mock_create.assert_called_once_with({
                "accountId": "acc_123",
                "amount": 250.0,
                "transactionType": "DEPOSIT",
                "description": "Test deposit"
            }, auth_token)
    
    @pytest.mark.asyncio
    async def test_withdraw_funds_success(self, transaction_client, auth_token):
        """Test successful withdrawal operation."""
        expected_response = {
            "id": "txn_790",
            "accountId": "acc_123",
            "amount": 100.00,
            "transactionType": "WITHDRAWAL",
            "status": "COMPLETED"
        }
        
        with patch.object(transaction_client, 'create_transaction', new_callable=AsyncMock) as mock_create:
            mock_create.return_value = expected_response
            
            result = await transaction_client.withdraw_funds(
                "acc_123", Decimal("100.00"), "Test withdrawal", auth_token
            )
            
            assert result == expected_response
            mock_create.assert_called_once_with({
                "accountId": "acc_123",
                "amount": 100.0,
                "transactionType": "WITHDRAWAL",
                "description": "Test withdrawal"
            }, auth_token)
    
    @pytest.mark.asyncio
    async def test_transfer_funds_success(self, transaction_client, auth_token):
        """Test successful transfer operation."""
        expected_response = {
            "id": "txn_791",
            "fromAccountId": "acc_123",
            "toAccountId": "acc_456",
            "amount": 150.00,
            "transactionType": "TRANSFER",
            "status": "COMPLETED"
        }
        
        with patch.object(transaction_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await transaction_client.transfer_funds(
                "acc_123", "acc_456", Decimal("150.00"), "Test transfer", auth_token
            )
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "POST", "/api/transactions/transfer", 
                data={
                    "fromAccountId": "acc_123",
                    "toAccountId": "acc_456",
                    "amount": 150.0,
                    "transactionType": "TRANSFER",
                    "description": "Test transfer"
                }, 
                auth_token=auth_token
            )
    
    @pytest.mark.asyncio
    async def test_get_transaction_history_success(self, transaction_client, auth_token):
        """Test successful transaction history retrieval."""
        expected_response = {
            "content": [
                {
                    "id": "txn_123",
                    "accountId": "acc_456",
                    "amount": 100.00,
                    "transactionType": "DEPOSIT",
                    "createdAt": "2024-01-01T10:00:00Z"
                }
            ],
            "totalElements": 1,
            "totalPages": 1,
            "number": 0
        }
        
        with patch.object(transaction_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await transaction_client.get_transaction_history(
                "acc_456", page=0, size=20, start_date="2024-01-01", auth_token=auth_token
            )
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "GET", "/api/transactions/history", 
                params={
                    "accountId": "acc_456",
                    "page": 0,
                    "size": 20,
                    "startDate": "2024-01-01"
                }, 
                auth_token=auth_token
            )
    
    @pytest.mark.asyncio
    async def test_reverse_transaction_success(self, transaction_client, auth_token):
        """Test successful transaction reversal."""
        expected_response = {
            "id": "txn_reverse_123",
            "originalTransactionId": "txn_123",
            "amount": -100.00,
            "transactionType": "REVERSAL",
            "status": "COMPLETED"
        }
        
        with patch.object(transaction_client, '_make_request', new_callable=AsyncMock) as mock_request:
            mock_request.return_value = expected_response
            
            result = await transaction_client.reverse_transaction(
                "txn_123", "Customer request", auth_token
            )
            
            assert result == expected_response
            mock_request.assert_called_once_with(
                "POST", "/api/transactions/txn_123/reverse",
                data={
                    "originalTransactionId": "txn_123",
                    "reason": "Customer request"
                },
                auth_token=auth_token
            )


class TestBaseHTTPClient:
    """Integration tests for base HTTP client functionality."""
    
    @pytest.fixture
    def base_client(self):
        """Create base HTTP client for testing."""
        return BaseHTTPClient("http://localhost:8080", timeout=5000)
    
    @pytest.mark.asyncio
    async def test_circuit_breaker_functionality(self, base_client):
        """Test circuit breaker behavior under failures."""
        # Mock consecutive failures
        with patch.object(base_client.client, 'request', new_callable=AsyncMock) as mock_request:
            mock_request.side_effect = httpx.ConnectError("Connection failed")
            
            # First few failures should be retried
            for i in range(3):
                with pytest.raises(ServiceUnavailableError):
                    await base_client.get("/test")
            
            # After threshold failures, circuit breaker should open
            base_client.circuit_breaker.failure_count = 5
            base_client.circuit_breaker.state = "OPEN"
            
            with pytest.raises(CircuitBreakerError):
                await base_client.get("/test")
    
    @pytest.mark.asyncio
    async def test_retry_logic_with_timeout(self, base_client):
        """Test retry logic with timeout exceptions."""
        with patch.object(base_client.client, 'request', new_callable=AsyncMock) as mock_request:
            # First two calls timeout, third succeeds
            mock_request.side_effect = [
                httpx.TimeoutException("Request timeout"),
                httpx.TimeoutException("Request timeout"),
                MagicMock(status_code=200, json=lambda: {"success": True})
            ]
            
            result = await base_client.get("/test")
            
            assert result == {"success": True}
            assert mock_request.call_count == 3
    
    @pytest.mark.asyncio
    async def test_health_check_functionality(self, base_client):
        """Test health check endpoint."""
        with patch.object(base_client, 'get', new_callable=AsyncMock) as mock_get:
            mock_get.return_value = {"status": "UP"}
            
            is_healthy = await base_client.health_check()
            
            assert is_healthy is True
            mock_get.assert_called_once_with("/actuator/health")
    
    @pytest.mark.asyncio
    async def test_health_check_failure(self, base_client):
        """Test health check when service is down."""
        with patch.object(base_client, 'get', new_callable=AsyncMock) as mock_get:
            mock_get.side_effect = ServiceUnavailableError("Service down")
            
            is_healthy = await base_client.health_check()
            
            assert is_healthy is False
    
    @pytest.mark.asyncio
    async def test_bearer_token_handling(self, base_client):
        """Test proper Bearer token handling in headers."""
        with patch.object(base_client.client, 'request', new_callable=AsyncMock) as mock_request:
            mock_response = MagicMock()
            mock_response.status_code = 200
            mock_response.json.return_value = {"success": True}
            mock_request.return_value = mock_response
            
            # Test with Bearer prefix
            await base_client.get("/test", auth_token="Bearer token123")
            
            call_args = mock_request.call_args
            headers = call_args.kwargs['headers']
            assert headers['Authorization'] == "Bearer token123"
            
            # Test without Bearer prefix (should be added)
            await base_client.get("/test", auth_token="token456")
            
            call_args = mock_request.call_args
            headers = call_args.kwargs['headers']
            assert headers['Authorization'] == "Bearer token456"
    
    @pytest.mark.asyncio
    async def test_service_unavailable_error_handling(self, base_client):
        """Test handling of 503 Service Unavailable responses."""
        with patch.object(base_client.client, 'request', new_callable=AsyncMock) as mock_request:
            mock_response = MagicMock()
            mock_response.status_code = 503
            mock_request.return_value = mock_response
            
            with pytest.raises(ServiceUnavailableError, match="Service unavailable"):
                await base_client.get("/test")
    
    @pytest.mark.asyncio
    async def test_no_content_response_handling(self, base_client):
        """Test handling of 204 No Content responses."""
        with patch.object(base_client.client, 'request', new_callable=AsyncMock) as mock_request:
            mock_response = MagicMock()
            mock_response.status_code = 204
            mock_request.return_value = mock_response
            
            result = await base_client.delete("/test")
            
            assert result == {}
    
    @pytest.mark.asyncio
    async def test_client_context_manager(self):
        """Test client as async context manager."""
        async with BaseHTTPClient("http://localhost:8080") as client:
            assert client.client is not None
            
        # Client should be closed after context exit
        assert client.client.is_closed


class TestServiceIntegrationScenarios:
    """Integration tests for realistic service interaction scenarios."""
    
    @pytest.fixture
    def account_client(self):
        return AccountServiceClient("http://localhost:8080")
    
    @pytest.fixture
    def transaction_client(self):
        return TransactionServiceClient("http://localhost:8081")
    
    @pytest.fixture
    def auth_token(self):
        return "Bearer valid.jwt.token"
    
    @pytest.mark.asyncio
    async def test_account_creation_and_transaction_flow(
        self, account_client, transaction_client, auth_token
    ):
        """Test complete flow: create account, deposit, check balance."""
        # Mock account creation
        with patch.object(account_client, 'create_account', new_callable=AsyncMock) as mock_create_account:
            mock_create_account.return_value = {
                "id": "acc_new_123",
                "ownerId": "user_456",
                "accountType": "CHECKING",
                "balance": 0.00,
                "status": "ACTIVE"
            }
            
            # Mock deposit transaction
            with patch.object(transaction_client, 'deposit_funds', new_callable=AsyncMock) as mock_deposit:
                mock_deposit.return_value = {
                    "id": "txn_deposit_123",
                    "accountId": "acc_new_123",
                    "amount": 1000.00,
                    "transactionType": "DEPOSIT",
                    "status": "COMPLETED"
                }
                
                # Mock balance check
                with patch.object(account_client, 'get_account_balance', new_callable=AsyncMock) as mock_balance:
                    mock_balance.return_value = {
                        "accountId": "acc_new_123",
                        "balance": 1000.00,
                        "availableBalance": 1000.00
                    }
                    
                    # Execute the flow
                    account = await account_client.create_account({
                        "ownerId": "user_456",
                        "accountType": "CHECKING",
                        "balance": 0.00
                    }, auth_token)
                    
                    transaction = await transaction_client.deposit_funds(
                        account["id"], Decimal("1000.00"), "Initial deposit", auth_token
                    )
                    
                    balance = await account_client.get_account_balance(account["id"], auth_token)
                    
                    # Verify the flow
                    assert account["id"] == "acc_new_123"
                    assert transaction["amount"] == 1000.00
                    assert balance["balance"] == 1000.00
    
    @pytest.mark.asyncio
    async def test_transfer_between_accounts_flow(
        self, account_client, transaction_client, auth_token
    ):
        """Test transfer flow between two accounts."""
        # Mock getting source account balance
        with patch.object(account_client, 'get_account_balance', new_callable=AsyncMock) as mock_balance:
            mock_balance.side_effect = [
                {"accountId": "acc_source", "balance": 1000.00, "availableBalance": 1000.00},
                {"accountId": "acc_dest", "balance": 500.00, "availableBalance": 500.00}
            ]
            
            # Mock transfer transaction
            with patch.object(transaction_client, 'transfer_funds', new_callable=AsyncMock) as mock_transfer:
                mock_transfer.return_value = {
                    "id": "txn_transfer_123",
                    "fromAccountId": "acc_source",
                    "toAccountId": "acc_dest",
                    "amount": 200.00,
                    "transactionType": "TRANSFER",
                    "status": "COMPLETED"
                }
                
                # Execute transfer flow
                source_balance = await account_client.get_account_balance("acc_source", auth_token)
                dest_balance = await account_client.get_account_balance("acc_dest", auth_token)
                
                transfer = await transaction_client.transfer_funds(
                    "acc_source", "acc_dest", Decimal("200.00"), "Transfer test", auth_token
                )
                
                # Verify transfer
                assert source_balance["balance"] >= 200.00  # Sufficient funds
                assert transfer["status"] == "COMPLETED"
                assert transfer["amount"] == 200.00
    
    @pytest.mark.asyncio
    async def test_transaction_history_and_analytics_flow(
        self, transaction_client, auth_token
    ):
        """Test transaction history and analytics retrieval."""
        # Mock transaction history
        with patch.object(transaction_client, 'get_transaction_history', new_callable=AsyncMock) as mock_history:
            mock_history.return_value = {
                "content": [
                    {"id": "txn_1", "amount": 100.00, "transactionType": "DEPOSIT"},
                    {"id": "txn_2", "amount": -50.00, "transactionType": "WITHDRAWAL"}
                ],
                "totalElements": 2
            }
            
            # Mock analytics
            with patch.object(transaction_client, 'get_transaction_analytics', new_callable=AsyncMock) as mock_analytics:
                mock_analytics.return_value = {
                    "totalTransactions": 2,
                    "totalDeposits": 100.00,
                    "totalWithdrawals": 50.00,
                    "netAmount": 50.00
                }
                
                # Execute analytics flow
                history = await transaction_client.get_transaction_history(
                    "acc_123", page=0, size=10, auth_token=auth_token
                )
                
                analytics = await transaction_client.get_transaction_analytics(
                    account_id="acc_123", auth_token=auth_token
                )
                
                # Verify results
                assert len(history["content"]) == 2
                assert analytics["totalTransactions"] == 2
                assert analytics["netAmount"] == 50.00