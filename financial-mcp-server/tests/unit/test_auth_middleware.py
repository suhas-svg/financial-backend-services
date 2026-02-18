"""
Unit tests for authentication middleware.
"""

import pytest
from unittest.mock import Mock, AsyncMock, patch
from mcp.types import TextContent

from mcp_financial.auth.middleware import AuthenticationMiddleware, create_auth_middleware
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext, AuthenticationError
from mcp_financial.auth.permissions import Permission


class TestAuthenticationMiddleware:
    """Test cases for AuthenticationMiddleware."""
    
    @pytest.fixture
    def jwt_handler(self):
        """Create mock JWT handler."""
        handler = Mock(spec=JWTAuthHandler)
        return handler
    
    @pytest.fixture
    def auth_middleware(self, jwt_handler):
        """Create authentication middleware."""
        return AuthenticationMiddleware(jwt_handler)
    
    @pytest.fixture
    def user_context(self):
        """Create user context for testing."""
        return UserContext(
            user_id="user123",
            username="testuser",
            roles=["customer"],
            permissions=["account:read"]
        )
    
    @pytest.mark.asyncio
    async def test_require_auth_success(self, auth_middleware, jwt_handler, user_context):
        """Test successful authentication."""
        # Setup mock
        jwt_handler.extract_user_context.return_value = user_context
        
        # Create decorated function
        @auth_middleware.require_auth()
        async def test_tool(auth_token: str, user_context: UserContext = None):
            return [TextContent(type="text", text=f"Success for {user_context.username}")]
        
        # Execute
        result = await test_tool(auth_token="valid_token")
        
        # Verify
        assert len(result) == 1
        assert result[0].text == "Success for testuser"
        jwt_handler.extract_user_context.assert_called_once_with("valid_token")
    
    @pytest.mark.asyncio
    async def test_require_auth_missing_token(self, auth_middleware):
        """Test authentication with missing token."""
        @auth_middleware.require_auth()
        async def test_tool(auth_token: str = None):
            return [TextContent(type="text", text="Should not reach here")]
        
        result = await test_tool()
        
        assert len(result) == 1
        assert "Authentication token is required" in result[0].text
    
    @pytest.mark.asyncio
    async def test_require_auth_invalid_token(self, auth_middleware, jwt_handler):
        """Test authentication with invalid token."""
        # Setup mock to raise authentication error
        jwt_handler.extract_user_context.side_effect = AuthenticationError("Invalid token")
        
        @auth_middleware.require_auth()
        async def test_tool(auth_token: str):
            return [TextContent(type="text", text="Should not reach here")]
        
        result = await test_tool(auth_token="invalid_token")
        
        assert len(result) == 1
        assert "Authentication failed - Invalid token" in result[0].text
    
    @pytest.mark.asyncio
    async def test_require_auth_with_permission_success(self, auth_middleware, jwt_handler, user_context):
        """Test authentication with required permission (success)."""
        # Setup mock
        jwt_handler.extract_user_context.return_value = user_context
        
        # Mock permission checker to return True
        with patch('mcp_financial.auth.middleware.PermissionChecker.has_permission', return_value=True):
            @auth_middleware.require_auth(required_permission=Permission.ACCOUNT_READ)
            async def test_tool(auth_token: str, user_context: UserContext = None):
                return [TextContent(type="text", text="Permission granted")]
            
            result = await test_tool(auth_token="valid_token")
            
            assert len(result) == 1
            assert result[0].text == "Permission granted"
    
    @pytest.mark.asyncio
    async def test_require_auth_with_permission_denied(self, auth_middleware, jwt_handler, user_context):
        """Test authentication with required permission (denied)."""
        # Setup mock
        jwt_handler.extract_user_context.return_value = user_context
        
        # Mock permission checker to return False
        with patch('mcp_financial.auth.middleware.PermissionChecker.has_permission', return_value=False):
            @auth_middleware.require_auth(required_permission=Permission.ADMIN_SYSTEM_STATUS)
            async def test_tool(auth_token: str, user_context: UserContext = None):
                return [TextContent(type="text", text="Should not reach here")]
            
            result = await test_tool(auth_token="valid_token")
            
            assert len(result) == 1
            assert "Insufficient permissions" in result[0].text
            assert "admin:system:status required" in result[0].text
    
    @pytest.mark.asyncio
    async def test_require_account_access_success(self, auth_middleware, jwt_handler, user_context):
        """Test account access requirement (success)."""
        # Setup mock
        jwt_handler.extract_user_context.return_value = user_context
        
        # Mock permission checker to return True
        with patch('mcp_financial.auth.middleware.PermissionChecker.can_access_account', return_value=True):
            @auth_middleware.require_account_access()
            async def test_tool(auth_token: str, owner_id: str, user_context: UserContext = None):
                return [TextContent(type="text", text=f"Access granted to {owner_id}")]
            
            result = await test_tool(auth_token="valid_token", owner_id="user123")
            
            assert len(result) == 1
            assert result[0].text == "Access granted to user123"
    
    @pytest.mark.asyncio
    async def test_require_account_access_denied(self, auth_middleware, jwt_handler, user_context):
        """Test account access requirement (denied)."""
        # Setup mock
        jwt_handler.extract_user_context.return_value = user_context
        
        # Mock permission checker to return False
        with patch('mcp_financial.auth.middleware.PermissionChecker.can_access_account', return_value=False):
            @auth_middleware.require_account_access()
            async def test_tool(auth_token: str, owner_id: str, user_context: UserContext = None):
                return [TextContent(type="text", text="Should not reach here")]
            
            result = await test_tool(auth_token="valid_token", owner_id="other_user")
            
            assert len(result) == 1
            assert "Access denied" in result[0].text
    
    @pytest.mark.asyncio
    async def test_require_account_access_custom_param(self, auth_middleware, jwt_handler, user_context):
        """Test account access with custom parameter name."""
        # Setup mock
        jwt_handler.extract_user_context.return_value = user_context
        
        # Mock permission checker to return True
        with patch('mcp_financial.auth.middleware.PermissionChecker.can_access_account', return_value=True):
            @auth_middleware.require_account_access(account_owner_param='account_id')
            async def test_tool(auth_token: str, account_id: str, user_context: UserContext = None):
                return [TextContent(type="text", text=f"Access granted to {account_id}")]
            
            result = await test_tool(auth_token="valid_token", account_id="acc123")
            
            assert len(result) == 1
            assert result[0].text == "Access granted to acc123"
    
    @pytest.mark.asyncio
    async def test_require_transaction_permission_success(self, auth_middleware, jwt_handler, user_context):
        """Test transaction permission requirement (success)."""
        # Setup mock
        jwt_handler.extract_user_context.return_value = user_context
        
        # Mock permission checker to return True
        with patch('mcp_financial.auth.middleware.PermissionChecker.can_perform_transaction', return_value=True):
            @auth_middleware.require_transaction_permission()
            async def test_tool(
                auth_token: str, 
                account_owner_id: str, 
                transaction_type: str,
                user_context: UserContext = None
            ):
                return [TextContent(type="text", text=f"Transaction {transaction_type} allowed")]
            
            result = await test_tool(
                auth_token="valid_token", 
                account_owner_id="user123", 
                transaction_type="DEPOSIT"
            )
            
            assert len(result) == 1
            assert result[0].text == "Transaction DEPOSIT allowed"
    
    @pytest.mark.asyncio
    async def test_require_transaction_permission_denied(self, auth_middleware, jwt_handler, user_context):
        """Test transaction permission requirement (denied)."""
        # Setup mock
        jwt_handler.extract_user_context.return_value = user_context
        
        # Mock permission checker to return False
        with patch('mcp_financial.auth.middleware.PermissionChecker.can_perform_transaction', return_value=False):
            @auth_middleware.require_transaction_permission()
            async def test_tool(
                auth_token: str, 
                account_owner_id: str, 
                transaction_type: str,
                user_context: UserContext = None
            ):
                return [TextContent(type="text", text="Should not reach here")]
            
            result = await test_tool(
                auth_token="valid_token", 
                account_owner_id="other_user", 
                transaction_type="WITHDRAWAL"
            )
            
            assert len(result) == 1
            assert "Transaction denied" in result[0].text
    
    @pytest.mark.asyncio
    async def test_middleware_exception_handling(self, auth_middleware, jwt_handler):
        """Test middleware exception handling."""
        # Setup mock to raise unexpected exception
        jwt_handler.extract_user_context.side_effect = Exception("Unexpected error")
        
        @auth_middleware.require_auth()
        async def test_tool(auth_token: str):
            return [TextContent(type="text", text="Should not reach here")]
        
        result = await test_tool(auth_token="valid_token")
        
        assert len(result) == 1
        assert "Internal server error during authentication" in result[0].text


class TestCreateAuthMiddleware:
    """Test cases for create_auth_middleware function."""
    
    def test_create_auth_middleware(self):
        """Test creating authentication middleware."""
        middleware = create_auth_middleware("test-secret")
        
        assert isinstance(middleware, AuthenticationMiddleware)
        assert isinstance(middleware.jwt_handler, JWTAuthHandler)
        assert middleware.jwt_handler.secret_key == "test-secret"


class TestConvenienceDecorators:
    """Test cases for convenience decorator functions."""
    
    def test_require_auth_decorator_exists(self):
        """Test that require_auth convenience decorator exists."""
        from mcp_financial.auth.middleware import require_auth
        
        @require_auth()
        def test_func():
            pass
        
        # Should not raise any errors
        assert callable(test_func)
    
    def test_require_account_access_decorator_exists(self):
        """Test that require_account_access convenience decorator exists."""
        from mcp_financial.auth.middleware import require_account_access
        
        @require_account_access()
        def test_func():
            pass
        
        # Should not raise any errors
        assert callable(test_func)
    
    def test_require_transaction_permission_decorator_exists(self):
        """Test that require_transaction_permission convenience decorator exists."""
        from mcp_financial.auth.middleware import require_transaction_permission
        
        @require_transaction_permission()
        def test_func():
            pass
        
        # Should not raise any errors
        assert callable(test_func)
