"""
Unit tests for transaction processing MCP tools.
"""

import pytest
import json
from unittest.mock import AsyncMock, MagicMock, patch
from decimal import Decimal
from datetime import datetime

from mcp.server.fastmcp import FastMCP
from mcp.types import TextContent

from mcp_financial.tools.transaction_tools import TransactionTools
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext, AuthenticationError
from mcp_financial.clients.transaction_client import TransactionServiceClient
from mcp_financial.clients.account_client import AccountServiceClient


class TestTransactionTools:
    """Test suite for TransactionTools class."""
    
    @pytest.fixture
    def mock_app(self):
        """Mock FastMCP application."""
        app = MagicMock(spec=FastMCP)
        app.tool = MagicMock(return_value=lambda func: func)
        return app
    
    @pytest.fixture
    def mock_transaction_client(self):
        """Mock TransactionServiceClient."""
        client = AsyncMock(spec=TransactionServiceClient)
        return client
    
    @pytest.fixture
    def mock_account_client(self):
        """Mock AccountServiceClient."""
        client = AsyncMock(spec=AccountServiceClient)
        return client
    
    @pytest.fixture
    def mock_auth_handler(self):
        """Mock JWTAuthHandler."""
        handler = MagicMock(spec=JWTAuthHandler)
        return handler
    
    @pytest.fixture
    def mock_user_context(self):
        """Mock UserContext."""
        return UserContext(
            user_id="test_user_123",
            username="testuser",
            roles=["financial_officer"],
            permissions=["transaction:create", "transaction:read", "transaction:reverse"]
        )
    
    @pytest.fixture
    def transaction_tools(self, mock_app, mock_transaction_client, mock_account_client, mock_auth_handler):
        """Create TransactionTools instance for testing."""
        with patch('mcp_financial.tools.transaction_tools.transaction_operations_counter'), \
             patch('mcp_financial.tools.transaction_tools.transaction_operation_duration'):
            
            captured_functions = {}
            
            def capture_tool():
                def decorator(func):
                    captured_functions[func.__name__] = func
                    return func
                return decorator
            
            mock_app.tool.side_effect = capture_tool
            
            tools = TransactionTools(mock_app, mock_transaction_client, mock_account_client, mock_auth_handler)
            
            # Store references to captured functions
            tools._deposit_funds = captured_functions.get('deposit_funds')
            tools._withdraw_funds = captured_functions.get('withdraw_funds')
            tools._transfer_funds = captured_functions.get('transfer_funds')
            tools._reverse_transaction = captured_functions.get('reverse_transaction')
            
            return tools
    
    @pytest.fixture
    def sample_transaction_data(self):
        """Sample transaction data for testing."""
        return {
            "id": "txn_test_123",
            "accountId": "acc_test_456",
            "amount": 100.00,
            "transactionType": "DEPOSIT",
            "status": "COMPLETED",
            "description": "Test transaction",
            "createdAt": "2024-01-01T10:00:00Z"
        }
    
    @pytest.mark.asyncio
    async def test_deposit_funds_success(
        self, 
        transaction_tools, 
        mock_auth_handler, 
        mock_transaction_client,
        mock_account_client,
        mock_user_context,
        sample_transaction_data
    ):
        """Test successful deposit operation."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.get_account.return_value = {
            "id": "acc_test_456",
            "ownerId": "test_user_123",
            "status": "ACTIVE"
        }
        mock_transaction_client.deposit_funds.return_value = sample_transaction_data
        
        # Get the registered function
        deposit_func = transaction_tools._deposit_funds
        assert deposit_func is not None, "deposit_funds function not found"
        
        # Test the function
        with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
            result = await deposit_func(
                account_id="acc_test_456",
                amount=100.0,
                description="Test deposit",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Deposit completed successfully" in response_data["message"]
        assert response_data["data"] == sample_transaction_data
        
        # Verify client calls
        mock_account_client.get_account.assert_called_once_with("acc_test_456", "valid_token")
        mock_transaction_client.deposit_funds.assert_called_once_with(
            "acc_test_456", Decimal("100.0"), "Test deposit", "valid_token"
        )
    
    @pytest.mark.asyncio
    async def test_withdraw_funds_success(
        self, 
        transaction_tools, 
        mock_auth_handler, 
        mock_transaction_client,
        mock_account_client,
        mock_user_context
    ):
        """Test successful withdrawal operation."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.get_account.return_value = {
            "id": "acc_test_456",
            "ownerId": "test_user_123",
            "status": "ACTIVE"
        }
        mock_account_client.get_account_balance.return_value = {
            "accountId": "acc_test_456",
            "balance": 500.00,
            "availableBalance": 500.00
        }
        
        withdrawal_data = {
            "id": "txn_withdrawal_123",
            "accountId": "acc_test_456",
            "amount": -100.00,
            "transactionType": "WITHDRAWAL",
            "status": "COMPLETED"
        }
        mock_transaction_client.withdraw_funds.return_value = withdrawal_data
        
        # Get the registered function
        withdraw_func = transaction_tools._withdraw_funds
        assert withdraw_func is not None, "withdraw_funds function not found"
        
        # Test the function
        with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
            result = await withdraw_func(
                account_id="acc_test_456",
                amount=100.0,
                description="Test withdrawal",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Withdrawal completed successfully" in response_data["message"]
        assert response_data["data"] == withdrawal_data
    
    @pytest.mark.asyncio
    async def test_withdraw_funds_insufficient_balance(
        self, 
        transaction_tools, 
        mock_auth_handler, 
        mock_account_client,
        mock_user_context
    ):
        """Test withdrawal with insufficient balance."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.get_account.return_value = {
            "id": "acc_test_456",
            "ownerId": "test_user_123",
            "status": "ACTIVE"
        }
        mock_account_client.get_account_balance.return_value = {
            "accountId": "acc_test_456",
            "balance": 50.00,  # Insufficient balance
            "availableBalance": 50.00
        }
        
        # Get the registered function
        withdraw_func = transaction_tools._withdraw_funds
        assert withdraw_func is not None, "withdraw_funds function not found"
        
        # Test the function
        with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
            result = await withdraw_func(
                account_id="acc_test_456",
                amount=100.0,  # More than available balance
                description="Test withdrawal",
                auth_token="valid_token"
            )
        
        # Verify error response
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "INSUFFICIENT_FUNDS"
        assert "Insufficient funds" in response_data["error_message"]
    
    @pytest.mark.asyncio
    async def test_transfer_funds_success(
        self, 
        transaction_tools, 
        mock_auth_handler, 
        mock_transaction_client,
        mock_account_client,
        mock_user_context
    ):
        """Test successful transfer operation."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.get_account.side_effect = [
            {"id": "acc_source", "ownerId": "test_user_123", "status": "ACTIVE"},
            {"id": "acc_dest", "ownerId": "other_user", "status": "ACTIVE"}
        ]
        mock_account_client.get_account_balance.return_value = {
            "accountId": "acc_source",
            "balance": 500.00,
            "availableBalance": 500.00
        }
        
        transfer_data = {
            "id": "txn_transfer_123",
            "fromAccountId": "acc_source",
            "toAccountId": "acc_dest",
            "amount": 150.00,
            "transactionType": "TRANSFER",
            "status": "COMPLETED"
        }
        mock_transaction_client.transfer_funds.return_value = transfer_data
        
        # Get the registered function
        transfer_func = transaction_tools._transfer_funds
        assert transfer_func is not None, "transfer_funds function not found"
        
        # Test the function
        with patch('mcp_financial.tools.transaction_tools.PermissionChecker.can_access_account', return_value=True):
            result = await transfer_func(
                from_account_id="acc_source",
                to_account_id="acc_dest",
                amount=150.0,
                description="Test transfer",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Transfer completed successfully" in response_data["message"]
        assert response_data["data"] == transfer_data
    
    @pytest.mark.asyncio
    async def test_transfer_funds_same_account_error(
        self, 
        transaction_tools, 
        mock_auth_handler, 
        mock_user_context
    ):
        """Test transfer to same account error."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        # Get the registered function
        transfer_func = transaction_tools._transfer_funds
        assert transfer_func is not None, "transfer_funds function not found"
        
        # Test with same account IDs
        result = await transfer_func(
            from_account_id="acc_same",
            to_account_id="acc_same",
            amount=100.0,
            description="Invalid transfer",
            auth_token="valid_token"
        )
        
        # Verify validation error
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "VALIDATION_ERROR"
        assert "Cannot transfer to the same account" in response_data["error_message"]
    
    @pytest.mark.asyncio
    async def test_reverse_transaction_success(
        self, 
        transaction_tools, 
        mock_auth_handler, 
        mock_transaction_client,
        mock_user_context
    ):
        """Test successful transaction reversal."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        reversal_data = {
            "id": "txn_reversal_123",
            "originalTransactionId": "txn_original_456",
            "amount": -100.00,
            "transactionType": "REVERSAL",
            "status": "COMPLETED",
            "reason": "Customer request"
        }
        mock_transaction_client.reverse_transaction.return_value = reversal_data
        
        # Get the registered function
        reverse_func = transaction_tools._reverse_transaction
        assert reverse_func is not None, "reverse_transaction function not found"
        
        # Test the function
        with patch('mcp_financial.tools.transaction_tools.PermissionChecker.has_permission', return_value=True):
            result = await reverse_func(
                transaction_id="txn_original_456",
                reason="Customer request",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Transaction reversed successfully" in response_data["message"]
        assert response_data["data"] == reversal_data
        
        # Verify client call
        mock_transaction_client.reverse_transaction.assert_called_once_with(
            "txn_original_456", "Customer request", "valid_token"
        )
    
    @pytest.mark.asyncio
    async def test_reverse_transaction_permission_denied(
        self, 
        transaction_tools, 
        mock_auth_handler, 
        mock_user_context
    ):
        """Test transaction reversal with insufficient permissions."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        # Get the registered function
        reverse_func = transaction_tools._reverse_transaction
        assert reverse_func is not None, "reverse_transaction function not found"
        
        # Test with permission denied
        with patch('mcp_financial.tools.transaction_tools.PermissionChecker.has_permission', return_value=False):
            result = await reverse_func(
                transaction_id="txn_original_456",
                reason="Customer request",
                auth_token="valid_token"
            )
        
        # Verify permission error
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "PERMISSION_DENIED"
        assert "Insufficient permissions" in response_data["error_message"]
    
    @pytest.mark.asyncio
    async def test_validation_errors(
        self, 
        transaction_tools, 
        mock_auth_handler, 
        mock_user_context
    ):
        """Test various validation errors."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        # Test negative amount
        deposit_func = transaction_tools._deposit_funds
        result = await deposit_func(
            account_id="acc_test",
            amount=-100.0,  # Invalid negative amount
            description="Test",
            auth_token="valid_token"
        )
        
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "VALIDATION_ERROR"
        assert "Amount must be positive" in response_data["error_message"]
        
        # Test zero amount
        result = await deposit_func(
            account_id="acc_test",
            amount=0.0,  # Invalid zero amount
            description="Test",
            auth_token="valid_token"
        )
        
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "VALIDATION_ERROR"
        assert "Amount must be positive" in response_data["error_message"]
        
        # Test empty account ID
        result = await deposit_func(
            account_id="",  # Invalid empty account ID
            amount=100.0,
            description="Test",
            auth_token="valid_token"
        )
        
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "VALIDATION_ERROR"
        assert "Account ID is required" in response_data["error_message"]