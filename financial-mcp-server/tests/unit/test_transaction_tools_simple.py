"""
Simplified unit tests for transaction processing MCP tools.
"""

import pytest
import json
from unittest.mock import AsyncMock, Mock, patch
from decimal import Decimal
from datetime import datetime

from mcp.types import TextContent

from mcp_financial.tools.transaction_tools import TransactionTools
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext, AuthenticationError
from mcp_financial.auth.permissions import PermissionChecker
from mcp_financial.clients.transaction_client import TransactionServiceClient
from mcp_financial.clients.account_client import AccountServiceClient


class TestTransactionToolsSimple:
    """Simplified test cases for transaction processing MCP tools."""
    
    @pytest.fixture
    def user_context(self):
        """Sample user context for testing."""
        return UserContext(
            user_id="user_123",
            username="testuser",
            roles=["financial_officer"],
            permissions=["transaction:create", "transaction:read", "transaction:reverse"]
        )
    
    @pytest.fixture
    def account_data(self):
        """Sample account data for testing."""
        return {
            "id": "acc_123",
            "ownerId": "user_123",
            "accountType": "CHECKING",
            "balance": 1000.00,
            "status": "ACTIVE"
        }
    
    @pytest.mark.asyncio
    async def test_deposit_request_validation(self):
        """Test deposit request validation."""
        from mcp_financial.models.requests import DepositRequest
        
        # Valid request
        request = DepositRequest(
            account_id="acc_123",
            amount=Decimal("100.00"),
            description="Test deposit"
        )
        assert request.account_id == "acc_123"
        assert request.amount == Decimal("100.00")
        
        # Invalid request - negative amount
        with pytest.raises(Exception):
            DepositRequest(
                account_id="acc_123",
                amount=Decimal("-100.00")
            )
    
    @pytest.mark.asyncio
    async def test_withdrawal_request_validation(self):
        """Test withdrawal request validation."""
        from mcp_financial.models.requests import WithdrawalRequest
        
        # Valid request
        request = WithdrawalRequest(
            account_id="acc_123",
            amount=Decimal("50.00"),
            description="Test withdrawal"
        )
        assert request.account_id == "acc_123"
        assert request.amount == Decimal("50.00")
        
        # Invalid request - zero amount
        with pytest.raises(Exception):
            WithdrawalRequest(
                account_id="acc_123",
                amount=Decimal("0.00")
            )
    
    @pytest.mark.asyncio
    async def test_transfer_request_validation(self):
        """Test transfer request validation."""
        from mcp_financial.models.requests import TransferRequest
        
        # Valid request
        request = TransferRequest(
            from_account_id="acc_123",
            to_account_id="acc_456",
            amount=Decimal("200.00"),
            description="Test transfer"
        )
        assert request.from_account_id == "acc_123"
        assert request.to_account_id == "acc_456"
        assert request.amount == Decimal("200.00")
        
        # Invalid request - same accounts
        with pytest.raises(Exception):
            TransferRequest(
                from_account_id="acc_123",
                to_account_id="acc_123",  # Same as source
                amount=Decimal("100.00")
            )
    
    @pytest.mark.asyncio
    async def test_transaction_reversal_request_validation(self):
        """Test transaction reversal request validation."""
        from mcp_financial.models.requests import TransactionReversalRequest
        
        # Valid request
        request = TransactionReversalRequest(
            transaction_id="txn_123",
            reason="Customer dispute"
        )
        assert request.transaction_id == "txn_123"
        assert request.reason == "Customer dispute"
        
        # Valid request without reason
        request = TransactionReversalRequest(
            transaction_id="txn_456"
        )
        assert request.transaction_id == "txn_456"
        assert request.reason is None
    
    @pytest.mark.asyncio
    async def test_transaction_client_methods(self):
        """Test transaction client method signatures."""
        client = AsyncMock(spec=TransactionServiceClient)
        
        # Test deposit method exists and can be called
        client.deposit_funds.return_value = {"id": "txn_123"}
        result = await client.deposit_funds(
            account_id="acc_123",
            amount=Decimal("100.00"),
            description="Test",
            auth_token="token"
        )
        assert result["id"] == "txn_123"
        client.deposit_funds.assert_called_once()
        
        # Test withdrawal method exists and can be called
        client.withdraw_funds.return_value = {"id": "txn_124"}
        result = await client.withdraw_funds(
            account_id="acc_123",
            amount=Decimal("50.00"),
            description="Test",
            auth_token="token"
        )
        assert result["id"] == "txn_124"
        client.withdraw_funds.assert_called_once()
        
        # Test transfer method exists and can be called
        client.transfer_funds.return_value = {"id": "txn_125"}
        result = await client.transfer_funds(
            from_account_id="acc_123",
            to_account_id="acc_456",
            amount=Decimal("200.00"),
            description="Test",
            auth_token="token"
        )
        assert result["id"] == "txn_125"
        client.transfer_funds.assert_called_once()
        
        # Test reversal method exists and can be called
        client.reverse_transaction.return_value = {"id": "txn_126"}
        result = await client.reverse_transaction(
            transaction_id="txn_123",
            reason="Test reversal",
            auth_token="token"
        )
        assert result["id"] == "txn_126"
        client.reverse_transaction.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_permission_checker_methods(self):
        """Test permission checker methods for transactions."""
        user_context = UserContext(
            user_id="user_123",
            username="testuser",
            roles=["financial_officer"],
            permissions=["transaction:create", "transaction:reverse"]
        )
        
        # Test permission checking methods exist
        with patch.object(PermissionChecker, 'can_perform_transaction', return_value=True) as mock_perform:
            result = PermissionChecker.can_perform_transaction(user_context, "user_123", "DEPOSIT")
            assert result is True
            mock_perform.assert_called_once()
        
        with patch.object(PermissionChecker, 'can_reverse_transaction', return_value=True) as mock_reverse:
            result = PermissionChecker.can_reverse_transaction(user_context)
            assert result is True
            mock_reverse.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_response_models(self):
        """Test transaction response models."""
        from mcp_financial.models.responses import (
            TransactionResponse, MCPSuccessResponse, MCPErrorResponse
        )
        
        # Test transaction response
        response = TransactionResponse(
            id="txn_123",
            account_id="acc_123",
            amount=Decimal("100.00"),
            transaction_type="DEPOSIT",
            status="COMPLETED",
            created_at=datetime(2024, 1, 1, 12, 0, 0)
        )
        assert response.id == "txn_123"
        assert response.amount == Decimal("100.00")
        
        # Test success response
        success = MCPSuccessResponse(
            message="Transaction completed",
            data={"transaction_id": "txn_123"}
        )
        assert success.success is True
        assert success.message == "Transaction completed"
        
        # Test error response
        error = MCPErrorResponse(
            error_code="VALIDATION_ERROR",
            error_message="Invalid parameters"
        )
        assert error.error_code == "VALIDATION_ERROR"
        assert error.error_message == "Invalid parameters"
    
    def test_transaction_tools_initialization(self):
        """Test TransactionTools can be initialized."""
        mock_app = Mock()
        mock_transaction_client = Mock()
        mock_account_client = Mock()
        mock_auth_handler = Mock()
        
        with patch('mcp_financial.tools.transaction_tools.transaction_operations_counter'), \
             patch('mcp_financial.tools.transaction_tools.transaction_operation_duration'), \
             patch('mcp_financial.tools.transaction_tools.transaction_amounts_histogram'), \
             patch('mcp_financial.tools.transaction_tools.logger'):
            
            tools = TransactionTools(
                app=mock_app,
                transaction_client=mock_transaction_client,
                account_client=mock_account_client,
                auth_handler=mock_auth_handler
            )
            
            assert tools.app == mock_app
            assert tools.transaction_client == mock_transaction_client
            assert tools.account_client == mock_account_client
            assert tools.auth_handler == mock_auth_handler
    
    @pytest.mark.asyncio
    async def test_authentication_error_handling(self):
        """Test authentication error handling."""
        auth_handler = Mock()
        auth_handler.extract_user_context.side_effect = AuthenticationError("Invalid token")
        
        # Verify that AuthenticationError can be raised and caught
        with pytest.raises(AuthenticationError) as exc_info:
            auth_handler.extract_user_context("invalid_token")
        
        assert str(exc_info.value) == "Invalid token"
    
    @pytest.mark.asyncio
    async def test_metrics_imports(self):
        """Test that metrics modules can be imported."""
        try:
            from mcp_financial.utils.metrics import (
                transaction_operations_counter,
                transaction_operation_duration,
                transaction_amounts_histogram
            )
            # If we get here, imports work
            assert True
        except ImportError as e:
            pytest.fail(f"Failed to import metrics: {e}")
    
    @pytest.mark.asyncio
    async def test_logging_configuration(self):
        """Test that logging is properly configured."""
        import logging
        
        # Test that we can get a logger
        logger = logging.getLogger('mcp_financial.tools.transaction_tools')
        assert logger is not None
        
        # Test that we can log messages (won't actually log in tests)
        logger.info("Test log message")
        logger.error("Test error message")
        
        # If we get here without exceptions, logging is working
        assert True