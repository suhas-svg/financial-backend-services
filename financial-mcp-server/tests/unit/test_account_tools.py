"""
Unit tests for account management MCP tools.
"""

import pytest
import json
from unittest.mock import AsyncMock, MagicMock, patch
from decimal import Decimal
from datetime import datetime

from mcp.server.fastmcp import FastMCP
from mcp.types import TextContent

from mcp_financial.tools.account_tools import AccountTools
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext, AuthenticationError
from mcp_financial.clients.account_client import AccountServiceClient
from mcp_financial.models import (
    AccountCreateRequest,
    AccountUpdateRequest,
    BalanceUpdateRequest,
    MCPErrorResponse,
    MCPSuccessResponse
)


class TestAccountTools:
    """Test suite for AccountTools class."""
    
    @pytest.fixture
    def mock_app(self):
        """Mock FastMCP application."""
        app = MagicMock(spec=FastMCP)
        app.tool = MagicMock(return_value=lambda func: func)  # Return the function unchanged
        return app
    
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
            permissions=["account:create", "account:read", "account:update", "account:delete", "account:balance:update"]
        )
    
    @pytest.fixture
    def account_tools(self, mock_app, mock_account_client, mock_auth_handler):
        """Create AccountTools instance for testing."""
        with patch('mcp_financial.tools.account_tools.account_operations_counter'), \
             patch('mcp_financial.tools.account_tools.account_operation_duration'):
            
            # Create a custom decorator that captures functions
            captured_functions = {}
            
            def capture_tool():
                def decorator(func):
                    captured_functions[func.__name__] = func
                    return func
                return decorator
            
            mock_app.tool.side_effect = capture_tool
            
            tools = AccountTools(mock_app, mock_account_client, mock_auth_handler)
            
            # Store references to the captured functions for testing
            tools._create_account = captured_functions.get('create_account')
            tools._get_account = captured_functions.get('get_account')
            tools._update_account = captured_functions.get('update_account')
            tools._delete_account = captured_functions.get('delete_account')
            tools._get_account_balance = captured_functions.get('get_account_balance')
            tools._update_account_balance = captured_functions.get('update_account_balance')
            
            return tools
    
    @pytest.fixture
    def sample_account_data(self):
        """Sample account data for testing."""
        return {
            "id": "acc_test_123",
            "ownerId": "test_user_123",
            "accountType": "CHECKING",
            "balance": 1000.00,
            "status": "ACTIVE",
            "createdAt": "2024-01-01T10:00:00Z",
            "updatedAt": "2024-01-01T10:00:00Z"
        }
    
    def test_account_tools_initialization(self, mock_app, mock_account_client, mock_auth_handler):
        """Test AccountTools initialization."""
        with patch('mcp_financial.tools.account_tools.account_operations_counter'), \
             patch('mcp_financial.tools.account_tools.account_operation_duration'):
            tools = AccountTools(mock_app, mock_account_client, mock_auth_handler)
            
            assert tools.app == mock_app
            assert tools.account_client == mock_account_client
            assert tools.auth_handler == mock_auth_handler
            
            # Verify that tools were registered
            assert mock_app.tool.called
    
    @pytest.mark.asyncio
    async def test_create_account_success(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_user_context,
        sample_account_data
    ):
        """Test successful account creation."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.create_account.return_value = sample_account_data
        
        # Get the registered create_account function
        create_account_func = account_tools._create_account
        assert create_account_func is not None, "create_account function not found"
        
        # Test the function
        with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
            result = await create_account_func(
                owner_id="test_user_123",
                account_type="CHECKING",
                initial_balance=1000.0,
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        assert isinstance(result[0], TextContent)
        
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Account created successfully" in response_data["message"]
        assert response_data["data"] == sample_account_data
        
        # Verify client was called correctly
        mock_account_client.create_account.assert_called_once()
        call_args = mock_account_client.create_account.call_args
        assert call_args[0][0]["ownerId"] == "test_user_123"
        assert call_args[0][0]["accountType"] == "CHECKING"
        assert call_args[0][0]["balance"] == 1000.0
    
    @pytest.mark.asyncio
    async def test_create_account_permission_denied(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_user_context
    ):
        """Test account creation with insufficient permissions."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        # Get the registered create_account function
        create_account_func = account_tools._create_account
        assert create_account_func is not None, "create_account function not found"
        
        # Test with permission denied
        with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=False):
            result = await create_account_func(
                owner_id="other_user",
                account_type="CHECKING",
                initial_balance=1000.0,
                auth_token="valid_token"
            )
        
        # Verify error response
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "PERMISSION_DENIED"
        assert "Insufficient permissions" in response_data["error_message"]
    
    @pytest.mark.asyncio
    async def test_create_account_validation_error(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_user_context
    ):
        """Test account creation with validation errors."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        # Get the registered create_account function
        create_account_func = account_tools._create_account
        assert create_account_func is not None, "create_account function not found"
        
        # Test with invalid parameters
        with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
            result = await create_account_func(
                owner_id="",  # Invalid empty owner_id
                account_type="INVALID_TYPE",
                initial_balance=-100.0,  # Invalid negative balance
                auth_token="valid_token"
            )
        
        # Verify validation error response
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "VALIDATION_ERROR"
        assert "Invalid request parameters" in response_data["error_message"]
    
    @pytest.mark.asyncio
    async def test_create_account_authentication_error(
        self, 
        account_tools, 
        mock_auth_handler
    ):
        """Test account creation with authentication error."""
        # Setup mocks
        mock_auth_handler.extract_user_context.side_effect = AuthenticationError("Invalid token")
        
        # Get the registered create_account function
        create_account_func = account_tools._create_account
        assert create_account_func is not None, "create_account function not found"
        
        # Test with invalid token
        result = await create_account_func(
            owner_id="test_user",
            account_type="CHECKING",
            initial_balance=1000.0,
            auth_token="invalid_token"
        )
        
        # Verify authentication error response
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "AUTHENTICATION_ERROR"
        assert "Invalid token" in response_data["error_message"]
    
    @pytest.mark.asyncio
    async def test_get_account_success(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_user_context,
        sample_account_data
    ):
        """Test successful account retrieval."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.get_account.return_value = sample_account_data
        
        # Get the registered get_account function
        get_account_func = account_tools._get_account
        assert get_account_func is not None, "get_account function not found"
        
        # Test the function
        with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
            result = await get_account_func(
                account_id="acc_test_123",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Account retrieved successfully" in response_data["message"]
        assert response_data["data"] == sample_account_data
        
        # Verify client was called correctly
        mock_account_client.get_account.assert_called_once_with("acc_test_123", "valid_token")
    
    @pytest.mark.asyncio
    async def test_update_account_success(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_user_context,
        sample_account_data
    ):
        """Test successful account update."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        updated_account = sample_account_data.copy()
        updated_account["accountType"] = "SAVINGS"
        mock_account_client.update_account.return_value = updated_account
        
        # Get the registered update_account function
        update_account_func = account_tools._update_account
        assert update_account_func is not None, "update_account function not found"
        
        # Test the function
        with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=True):
            result = await update_account_func(
                account_id="acc_test_123",
                account_type="SAVINGS",
                status="ACTIVE",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Account updated successfully" in response_data["message"]
        assert response_data["data"] == updated_account
        
        # Verify client was called correctly
        mock_account_client.update_account.assert_called_once()
        call_args = mock_account_client.update_account.call_args
        assert call_args[0][0] == "acc_test_123"
        assert call_args[0][1]["accountType"] == "SAVINGS"
        assert call_args[0][1]["status"] == "ACTIVE"
    
    @pytest.mark.asyncio
    async def test_update_account_no_fields(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_user_context
    ):
        """Test account update with no fields provided."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        # Get the registered update_account function
        update_account_func = account_tools._update_account
        assert update_account_func is not None, "update_account function not found"
        
        # Test with no update fields
        with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=True):
            result = await update_account_func(
                account_id="acc_test_123",
                account_type=None,
                status=None,
                auth_token="valid_token"
            )
        
        # Verify validation error response
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "VALIDATION_ERROR"
        assert "No update fields provided" in response_data["error_message"]
    
    @pytest.mark.asyncio
    async def test_delete_account_success(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_user_context
    ):
        """Test successful account deletion."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.delete_account.return_value = {"success": True, "message": "Account deleted"}
        
        # Get the registered delete_account function
        delete_account_func = account_tools._delete_account
        assert delete_account_func is not None, "delete_account function not found"
        
        # Test the function
        with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=True):
            result = await delete_account_func(
                account_id="acc_test_123",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Account deleted successfully" in response_data["message"]
        
        # Verify client was called correctly
        mock_account_client.delete_account.assert_called_once_with("acc_test_123", "valid_token")
    
    @pytest.mark.asyncio
    async def test_get_account_balance_success(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_user_context,
        sample_account_data
    ):
        """Test successful balance retrieval."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.get_account.return_value = sample_account_data
        balance_data = {
            "accountId": "acc_test_123",
            "balance": 1000.00,
            "availableBalance": 1000.00,
            "lastUpdated": "2024-01-01T10:00:00Z"
        }
        mock_account_client.get_account_balance.return_value = balance_data
        
        # Get the registered get_account_balance function
        get_balance_func = account_tools._get_account_balance
        assert get_balance_func is not None, "get_account_balance function not found"
        
        # Test the function
        with patch('mcp_financial.tools.account_tools.PermissionChecker.can_access_account', return_value=True):
            result = await get_balance_func(
                account_id="acc_test_123",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Account balance retrieved successfully" in response_data["message"]
        assert response_data["data"] == balance_data
    
    @pytest.mark.asyncio
    async def test_update_account_balance_success(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_user_context
    ):
        """Test successful balance update."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        updated_balance = {
            "accountId": "acc_test_123",
            "balance": 1500.00,
            "lastUpdated": "2024-01-01T11:00:00Z"
        }
        mock_account_client.update_account_balance.return_value = updated_balance
        
        # Get the registered update_account_balance function
        update_balance_func = account_tools._update_account_balance
        assert update_balance_func is not None, "update_account_balance function not found"
        
        # Test the function
        with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=True):
            result = await update_balance_func(
                account_id="acc_test_123",
                new_balance=1500.0,
                reason="Balance adjustment",
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["success"] is True
        assert "Account balance updated successfully" in response_data["message"]
        assert response_data["data"] == updated_balance
        
        # Verify client was called correctly
        mock_account_client.update_account_balance.assert_called_once()
        call_args = mock_account_client.update_account_balance.call_args
        assert call_args[0][0] == "acc_test_123"
        assert call_args[0][1]["balance"] == 1500.0
        assert call_args[0][1]["reason"] == "Balance adjustment"
    
    @pytest.mark.asyncio
    async def test_update_account_balance_validation_error(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_user_context
    ):
        """Test balance update with validation error."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        # Get the registered update_account_balance function
        update_balance_func = account_tools._update_account_balance
        assert update_balance_func is not None, "update_account_balance function not found"
        
        # Test with negative balance
        with patch('mcp_financial.tools.account_tools.PermissionChecker.has_permission', return_value=True):
            result = await update_balance_func(
                account_id="acc_test_123",
                new_balance=-100.0,  # Invalid negative balance
                reason="Test",
                auth_token="valid_token"
            )
        
        # Verify validation error response
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "VALIDATION_ERROR"
        assert "Invalid balance update parameters" in response_data["error_message"]
    
    @pytest.mark.asyncio
    async def test_service_error_handling(
        self, 
        account_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_user_context
    ):
        """Test handling of service errors."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        mock_account_client.create_account.side_effect = Exception("Service unavailable")
        
        # Get the registered create_account function
        create_account_func = account_tools._create_account
        assert create_account_func is not None, "create_account function not found"
        
        # Test with service error
        with patch('mcp_financial.tools.account_tools.PermissionChecker.can_create_account', return_value=True):
            result = await create_account_func(
                owner_id="test_user",
                account_type="CHECKING",
                initial_balance=1000.0,
                auth_token="valid_token"
            )
        
        # Verify error response
        assert len(result) == 1
        response_data = json.loads(result[0].text)
        assert response_data["error_code"] == "INTERNAL_ERROR"
        assert "Failed to create account" in response_data["error_message"]
        assert "Service unavailable" in response_data["error_message"]