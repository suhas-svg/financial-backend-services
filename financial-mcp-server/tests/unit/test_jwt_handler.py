"""
Unit tests for JWT authentication handler.
"""

import pytest
import jwt
from datetime import datetime, timedelta
from unittest.mock import patch

from mcp_financial.auth.jwt_handler import (
    JWTAuthHandler,
    UserContext,
    AuthenticationError
)


class TestJWTAuthHandler:
    """Test cases for JWTAuthHandler."""
    
    @pytest.fixture
    def auth_handler(self):
        """Create JWT auth handler for testing."""
        return JWTAuthHandler(secret_key="test-secret-key")
    
    @pytest.fixture
    def valid_token_payload(self):
        """Valid token payload for testing."""
        return {
            'sub': 'user123',
            'username': 'testuser',
            'roles': ['customer', 'account_manager'],
            'permissions': ['account:read', 'transaction:read'],
            'iat': int(datetime.utcnow().timestamp()),
            'exp': int((datetime.utcnow() + timedelta(hours=1)).timestamp())
        }
    
    def test_validate_token_success(self, auth_handler, valid_token_payload):
        """Test successful token validation."""
        # Create a valid token
        token = jwt.encode(valid_token_payload, "test-secret-key", algorithm="HS256")
        
        # Validate token
        claims = auth_handler.validate_token(token)
        
        assert claims['sub'] == 'user123'
        assert claims['username'] == 'testuser'
        assert claims['roles'] == ['customer', 'account_manager']
        assert claims['permissions'] == ['account:read', 'transaction:read']
    
    def test_validate_token_with_bearer_prefix(self, auth_handler, valid_token_payload):
        """Test token validation with Bearer prefix."""
        token = jwt.encode(valid_token_payload, "test-secret-key", algorithm="HS256")
        bearer_token = f"Bearer {token}"
        
        claims = auth_handler.validate_token(bearer_token)
        
        assert claims['sub'] == 'user123'
    
    def test_validate_token_expired(self, auth_handler):
        """Test validation of expired token."""
        expired_payload = {
            'sub': 'user123',
            'username': 'testuser',
            'roles': ['customer'],
            'iat': int((datetime.utcnow() - timedelta(hours=2)).timestamp()),
            'exp': int((datetime.utcnow() - timedelta(hours=1)).timestamp())
        }
        
        token = jwt.encode(expired_payload, "test-secret-key", algorithm="HS256")
        
        with pytest.raises(AuthenticationError, match="Token has expired"):
            auth_handler.validate_token(token)
    
    def test_validate_token_invalid_signature(self, auth_handler, valid_token_payload):
        """Test validation of token with invalid signature."""
        token = jwt.encode(valid_token_payload, "wrong-secret", algorithm="HS256")
        
        with pytest.raises(AuthenticationError, match="Invalid token"):
            auth_handler.validate_token(token)
    
    def test_validate_token_malformed(self, auth_handler):
        """Test validation of malformed token."""
        with pytest.raises(AuthenticationError, match="Invalid token"):
            auth_handler.validate_token("invalid.token.format")
    
    def test_extract_user_context_success(self, auth_handler, valid_token_payload):
        """Test successful user context extraction."""
        token = jwt.encode(valid_token_payload, "test-secret-key", algorithm="HS256")
        
        user_context = auth_handler.extract_user_context(token)
        
        assert isinstance(user_context, UserContext)
        assert user_context.user_id == 'user123'
        assert user_context.username == 'testuser'
        assert user_context.roles == ['customer', 'account_manager']
        assert user_context.permissions == ['account:read', 'transaction:read']
    
    def test_extract_user_context_minimal_claims(self, auth_handler):
        """Test user context extraction with minimal claims."""
        minimal_payload = {
            'sub': 'user456',
            'iat': int(datetime.utcnow().timestamp()),
            'exp': int((datetime.utcnow() + timedelta(hours=1)).timestamp())
        }
        
        token = jwt.encode(minimal_payload, "test-secret-key", algorithm="HS256")
        user_context = auth_handler.extract_user_context(token)
        
        assert user_context.user_id == 'user456'
        assert user_context.username == ''
        assert user_context.roles == []
        assert user_context.permissions == []
    
    def test_extract_user_context_string_roles(self, auth_handler):
        """Test user context extraction when roles is a string."""
        payload = {
            'sub': 'user789',
            'username': 'testuser',
            'roles': 'admin',  # Single role as string
            'permissions': 'admin:all',  # Single permission as string
            'iat': int(datetime.utcnow().timestamp()),
            'exp': int((datetime.utcnow() + timedelta(hours=1)).timestamp())
        }
        
        token = jwt.encode(payload, "test-secret-key", algorithm="HS256")
        user_context = auth_handler.extract_user_context(token)
        
        assert user_context.roles == ['admin']
        assert user_context.permissions == ['admin:all']
    
    def test_extract_user_context_invalid_token(self, auth_handler):
        """Test user context extraction with invalid token."""
        with pytest.raises(AuthenticationError):
            auth_handler.extract_user_context("invalid.token")
    
    def test_create_token(self, auth_handler):
        """Test token creation."""
        token = auth_handler.create_token(
            user_id="test_user",
            username="testuser",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=3600
        )
        
        # Validate the created token
        claims = auth_handler.validate_token(token)
        
        assert claims['sub'] == 'test_user'
        assert claims['username'] == 'testuser'
        assert claims['roles'] == ['customer']
        assert claims['permissions'] == ['account:read']
    
    def test_create_token_defaults(self, auth_handler):
        """Test token creation with default values."""
        token = auth_handler.create_token(
            user_id="test_user",
            username="testuser"
        )
        
        claims = auth_handler.validate_token(token)
        
        assert claims['roles'] == []
        assert claims['permissions'] == []


class TestUserContext:
    """Test cases for UserContext."""
    
    @pytest.fixture
    def user_context(self):
        """Create user context for testing."""
        return UserContext(
            user_id="user123",
            username="testuser",
            roles=["customer", "account_manager"],
            permissions=["account:read", "transaction:read", "account:create"]
        )
    
    def test_has_role_success(self, user_context):
        """Test successful role check."""
        assert user_context.has_role("customer") is True
        assert user_context.has_role("account_manager") is True
    
    def test_has_role_failure(self, user_context):
        """Test failed role check."""
        assert user_context.has_role("admin") is False
        assert user_context.has_role("nonexistent") is False
    
    def test_has_permission_success(self, user_context):
        """Test successful permission check."""
        assert user_context.has_permission("account:read") is True
        assert user_context.has_permission("transaction:read") is True
        assert user_context.has_permission("account:create") is True
    
    def test_has_permission_failure(self, user_context):
        """Test failed permission check."""
        assert user_context.has_permission("admin:all") is False
        assert user_context.has_permission("nonexistent") is False
    
    def test_user_context_creation(self):
        """Test UserContext creation."""
        context = UserContext(
            user_id="123",
            username="test",
            roles=["role1"],
            permissions=["perm1"]
        )
        
        assert context.user_id == "123"
        assert context.username == "test"
        assert context.roles == ["role1"]
        assert context.permissions == ["perm1"]


class TestAuthenticationError:
    """Test cases for AuthenticationError."""
    
    def test_authentication_error_creation(self):
        """Test AuthenticationError creation."""
        error = AuthenticationError("Test error message")
        assert str(error) == "Test error message"
    
    def test_authentication_error_inheritance(self):
        """Test AuthenticationError inheritance."""
        error = AuthenticationError("Test error")
        assert isinstance(error, Exception)