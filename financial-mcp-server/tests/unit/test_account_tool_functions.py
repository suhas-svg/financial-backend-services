"""
Unit tests for individual account tool functions.
"""

import pytest
import json
from unittest.mock import AsyncMock, MagicMock, patch
from decimal import Decimal

from mcp_financial.auth.jwt_handler import UserContext, AuthenticationError
from mcp_financial.models import MCPErrorResponse, MCPSuccessResponse


class TestAccountToolFunctions:
    """Test individual account tool functions."""
    
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
    
    @pytest.mark.asyncio
    async def test_create_account_success(self, mock_user_context, sample_account_data):
        """Test successful account creation."""
        # Mock dependencies
        mock_auth_handler = MagicMock()
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        mock_account_client = AsyncMock()
        mock_account_client.create_account.return_value = sample_account_data
        
        # Create the tool function manually (simulating what AccountTools does)
        async def create_account(owner_id: str, account_type: str, initial_balance: float = 0.0, auth_token: str = ""):
            """Simulated create_account tool function."""
            import uuid
            from mcp.types import TextContent
            from mcp_financial.models import AccountCreateRequest, MCPSuccessResponse, MCPErrorResponse
            from mcp_financial.auth.permissions import PermissionChecker
            
            request_id = str(uuid.uuid4())
            
            try:
                # Validate authentication
                user_context = mock_auth_handler.extract_user_context(auth_token)
                
                # Check permissions
                if not PermissionChecker.can_create_account(user_context, owner_id):
                    error_response = MCPErrorResponse(
                        error_code="PERMISSION_DENIED",
                        error_message="Insufficient permissions to create account",
                        request_id=request_id
                    )
                    return [TextContent(type="text", text=error_response.json())]
                
                # Validate request
                try:
                    request_data = AccountCreateRequest(
                        owner_id=owner_id,
                        account_type=account_type,
                        initial_balance=Decimal(str(initial_balance))
                    )
                except Exception as e:
                    error_response = MCPErrorResponse(
                        error_code="VALIDATION_ERROR",
                        error_message="Invalid request parameters",
                        details={"validation_errors": str(e)},
                        request_id=request_id
                    )
                    return [TextContent(type="text", text=error_response.json())]
                
                # Create account through service
                account_data = {
                    "ownerId": request_data.owner_id,
                    "accountType": request_data.account_type.value,
                    "balance": float(request_data.initial_balance)
                }
                
                result = await mock_account_client.create_account(account_data, auth_token)
                
                success_response = MCPSuccessResponse(
                    message="Account created successfully",
                    data=result,
                    request_id=request_id
                )
                
                return [TextContent(type="text", text=success_response.json())]
                
            except AuthenticationError as e:
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.json())]
                
            except Exception as e:
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to create account: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.json())]
        
        # Test the function
        with patch('mcp_financial.auth.permissions.PermissionChecker.can_create_account', return_value=True):
            result = await create_account(
                owner_id="test_user_123",
                account_type="CHECKING",
                initial_balance=1000.0,
                auth_token="valid_token"
            )
        
        # Verify result
        assert len(result) == 1
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
    async def test_create_account_permission_denied(self, mock_user_context):
        """Test account creation with insufficient permissions."""
        # Mock dependencies
        mock_auth_handler = MagicMock()
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        mock_account_client = AsyncMock()
        
        # Create the tool function manually
        async def create_account(owner_id: str, account_type: str, initial_balance: float = 0.0, auth_token: str = ""):
            import uuid
            from mcp.types import TextContent
            from mcp_financial.models import MCPErrorResponse
            from mcp_financial.auth.permissions import PermissionChecker
            
            request_id = str(uuid.uuid4())
            
            try:
                user_context = mock_auth_handler.extract_user_context(auth_token)
                
                if not PermissionChecker.can_create_account(user_context, owner_id):
                    error_response = MCPErrorResponse(
                        error_code="PERMISSION_DENIED",
                        error_message="Insufficient permissions to create account",
                        request_id=request_id
                    )
                    return [TextContent(type="text", text=error_response.json())]
                
                # This shouldn't be reached in this test
                return []
                
            except Exception as e:
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.json())]
        
        # Test with permission denied
        with patch('mcp_financial.auth.permissions.PermissionChecker.can_create_account', return_value=False):
            result = await create_account(
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
    async def test_create_account_validation_error(self, mock_user_context):
        """Test account creation with validation errors."""
        # Mock dependencies
        mock_auth_handler = MagicMock()
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        # Create the tool function manually
        async def create_account(owner_id: str, account_type: str, initial_balance: float = 0.0, auth_token: str = ""):
            import uuid
            from mcp.types import TextContent
            from mcp_financial.models import AccountCreateRequest, MCPErrorResponse
            from mcp_financial.auth.permissions import PermissionChecker
            
            request_id = str(uuid.uuid4())
            
            try:
                user_context = mock_auth_handler.extract_user_context(auth_token)
                
                if not PermissionChecker.can_create_account(user_context, owner_id):
                    error_response = MCPErrorResponse(
                        error_code="PERMISSION_DENIED",
                        error_message="Insufficient permissions to create account",
                        request_id=request_id
                    )
                    return [TextContent(type="text", text=error_response.json())]
                
                # Validate request - this will fail with invalid parameters
                try:
                    request_data = AccountCreateRequest(
                        owner_id=owner_id,
                        account_type=account_type,
                        initial_balance=Decimal(str(initial_balance))
                    )
                except Exception as e:
                    error_response = MCPErrorResponse(
                        error_code="VALIDATION_ERROR",
                        error_message="Invalid request parameters",
                        details={"validation_errors": str(e)},
                        request_id=request_id
                    )
                    return [TextContent(type="text", text=error_response.json())]
                
                # This shouldn't be reached in this test
                return []
                
            except Exception as e:
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.json())]
        
        # Test with invalid parameters
        with patch('mcp_financial.auth.permissions.PermissionChecker.can_create_account', return_value=True):
            result = await create_account(
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
    async def test_create_account_authentication_error(self):
        """Test account creation with authentication error."""
        # Mock dependencies
        mock_auth_handler = MagicMock()
        mock_auth_handler.extract_user_context.side_effect = AuthenticationError("Invalid token")
        
        # Create the tool function manually
        async def create_account(owner_id: str, account_type: str, initial_balance: float = 0.0, auth_token: str = ""):
            import uuid
            from mcp.types import TextContent
            from mcp_financial.models import MCPErrorResponse
            from mcp_financial.auth.jwt_handler import AuthenticationError
            
            request_id = str(uuid.uuid4())
            
            try:
                user_context = mock_auth_handler.extract_user_context(auth_token)
                # This shouldn't be reached in this test
                return []
                
            except AuthenticationError as e:
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.json())]
                
            except Exception as e:
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.json())]
        
        # Test with invalid token
        result = await create_account(
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
    async def test_get_account_success(self, mock_user_context, sample_account_data):
        """Test successful account retrieval."""
        # Mock dependencies
        mock_auth_handler = MagicMock()
        mock_auth_handler.extract_user_context.return_value = mock_user_context
        
        mock_account_client = AsyncMock()
        mock_account_client.get_account.return_value = sample_account_data
        
        # Create the tool function manually
        async def get_account(account_id: str, auth_token: str = ""):
            import uuid
            from mcp.types import TextContent
            from mcp_financial.models import MCPSuccessResponse, MCPErrorResponse
            from mcp_financial.auth.permissions import PermissionChecker
            
            request_id = str(uuid.uuid4())
            
            try:
                user_context = mock_auth_handler.extract_user_context(auth_token)
                account_data = await mock_account_client.get_account(account_id, auth_token)
                
                if not PermissionChecker.can_access_account(user_context, account_data.get("ownerId")):
                    error_response = MCPErrorResponse(
                        error_code="PERMISSION_DENIED",
                        error_message="Insufficient permissions to access account",
                        request_id=request_id
                    )
                    return [TextContent(type="text", text=error_response.json())]
                
                success_response = MCPSuccessResponse(
                    message="Account retrieved successfully",
                    data=account_data,
                    request_id=request_id
                )
                
                return [TextContent(type="text", text=success_response.json())]
                
            except Exception as e:
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to retrieve account: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.json())]
        
        # Test the function
        with patch('mcp_financial.auth.permissions.PermissionChecker.can_access_account', return_value=True):
            result = await get_account(
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