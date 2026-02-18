"""
Integration tests for JWT compatibility with existing services.
"""

import pytest
from datetime import datetime, timedelta

from mcp_financial.auth.jwt_handler import JWTAuthHandler
from mcp_financial.auth.permissions import PermissionChecker, Permission


class TestJWTCompatibility:
    """Test JWT compatibility with existing financial services."""
    
    @pytest.fixture
    def jwt_secret(self):
        """JWT secret key used by existing services."""
        return "AY8Ro0HSBFyllm9ZPafT2GWuE/t8Yzq1P0Rf7bNeq14="
    
    @pytest.fixture
    def auth_handler(self, jwt_secret):
        """Create JWT auth handler with production secret."""
        return JWTAuthHandler(jwt_secret)
    
    def test_jwt_token_compatibility(self, auth_handler):
        """Test that JWT tokens are compatible with existing services."""
        # Create a token similar to what the existing services would create
        token = auth_handler.create_token(
            user_id="test_user_123",
            username="testuser",
            roles=["financial_officer", "account_manager"],
            permissions=["account:create", "transaction:create", "account:read"],
            expires_in=3600
        )
        
        # Validate the token
        claims = auth_handler.validate_token(token)
        
        # Verify token structure matches expected format
        assert claims['sub'] == 'test_user_123'
        assert claims['username'] == 'testuser'
        assert 'financial_officer' in claims['roles']
        assert 'account_manager' in claims['roles']
        assert 'account:create' in claims['permissions']
        assert 'iat' in claims
        assert 'exp' in claims
    
    def test_user_context_extraction_compatibility(self, auth_handler):
        """Test user context extraction with realistic data."""
        # Create token with realistic user data
        token = auth_handler.create_token(
            user_id="emp_12345",
            username="john.doe",
            roles=["financial_officer"],
            permissions=["account:create", "account:read", "transaction:create"],
            expires_in=3600
        )
        
        # Extract user context
        user_context = auth_handler.extract_user_context(token)
        
        # Verify user context
        assert user_context.user_id == "emp_12345"
        assert user_context.username == "john.doe"
        assert user_context.has_role("financial_officer")
        assert user_context.has_permission("account:create")
        assert user_context.has_permission("transaction:create")
    
    def test_permission_checking_with_realistic_roles(self, auth_handler):
        """Test permission checking with realistic role scenarios."""
        # Test admin user
        admin_token = auth_handler.create_token(
            user_id="admin_001",
            username="admin",
            roles=["admin"],
            permissions=[],
            expires_in=3600
        )
        
        admin_context = auth_handler.extract_user_context(admin_token)
        
        # Admin should have all permissions
        assert PermissionChecker.has_permission(admin_context, Permission.ACCOUNT_CREATE)
        assert PermissionChecker.has_permission(admin_context, Permission.TRANSACTION_REVERSE)
        assert PermissionChecker.has_permission(admin_context, Permission.ADMIN_SYSTEM_STATUS)
        assert PermissionChecker.can_create_account(admin_context, "any_user")
        assert PermissionChecker.can_reverse_transaction(admin_context)
        
        # Test financial officer
        fo_token = auth_handler.create_token(
            user_id="fo_001",
            username="financial.officer",
            roles=["financial_officer"],
            permissions=[],
            expires_in=3600
        )
        
        fo_context = auth_handler.extract_user_context(fo_token)
        
        # Financial officer should have financial permissions but not admin
        assert PermissionChecker.has_permission(fo_context, Permission.ACCOUNT_CREATE)
        assert PermissionChecker.has_permission(fo_context, Permission.TRANSACTION_REVERSE)
        assert not PermissionChecker.has_permission(fo_context, Permission.ADMIN_SYSTEM_STATUS)
        assert PermissionChecker.can_create_account(fo_context, "any_user")
        assert PermissionChecker.can_reverse_transaction(fo_context)
        
        # Test customer
        customer_token = auth_handler.create_token(
            user_id="cust_001",
            username="customer",
            roles=["customer"],
            permissions=[],
            expires_in=3600
        )
        
        customer_context = auth_handler.extract_user_context(customer_token)
        
        # Customer should have limited permissions
        assert PermissionChecker.has_permission(customer_context, Permission.ACCOUNT_READ)
        assert not PermissionChecker.has_permission(customer_context, Permission.ACCOUNT_CREATE)
        assert not PermissionChecker.has_permission(customer_context, Permission.TRANSACTION_REVERSE)
        assert not PermissionChecker.can_create_account(customer_context, "any_user")
        assert not PermissionChecker.can_reverse_transaction(customer_context)
        
        # Customer can access their own account
        assert PermissionChecker.can_access_account(customer_context, "cust_001")
        assert not PermissionChecker.can_access_account(customer_context, "other_user")
    
    def test_bearer_token_format_compatibility(self, auth_handler):
        """Test Bearer token format compatibility."""
        token = auth_handler.create_token(
            user_id="test_user",
            username="testuser",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=3600
        )
        
        # Test with Bearer prefix (as would come from HTTP Authorization header)
        bearer_token = f"Bearer {token}"
        
        # Should work with Bearer prefix
        claims = auth_handler.validate_token(bearer_token)
        assert claims['sub'] == 'test_user'
        
        # Should also work without Bearer prefix
        claims_direct = auth_handler.validate_token(token)
        assert claims_direct['sub'] == 'test_user'
        
        # Claims should be identical
        assert claims == claims_direct
    
    def test_token_expiration_handling(self, auth_handler):
        """Test token expiration handling."""
        # Create a short-lived token
        token = auth_handler.create_token(
            user_id="test_user",
            username="testuser",
            roles=["customer"],
            permissions=["account:read"],
            expires_in=1  # 1 second
        )
        
        # Token should be valid immediately
        claims = auth_handler.validate_token(token)
        assert claims['sub'] == 'test_user'
        
        # Wait for token to expire (in real test, we'd mock time)
        import time
        time.sleep(2)
        
        # Token should now be expired
        from mcp_financial.auth.jwt_handler import AuthenticationError
        with pytest.raises(AuthenticationError, match="Token has expired"):
            auth_handler.validate_token(token)
    
    def test_invalid_token_handling(self, auth_handler):
        """Test handling of invalid tokens."""
        from mcp_financial.auth.jwt_handler import AuthenticationError
        
        # Test completely invalid token
        with pytest.raises(AuthenticationError, match="Invalid token"):
            auth_handler.validate_token("invalid.token.format")
        
        # Test token with wrong signature
        wrong_secret_handler = JWTAuthHandler("wrong-secret")
        valid_token = auth_handler.create_token("user", "username", ["role"])
        
        with pytest.raises(AuthenticationError, match="Invalid token"):
            wrong_secret_handler.validate_token(valid_token)
    
    def test_role_based_access_scenarios(self, auth_handler):
        """Test realistic role-based access scenarios."""
        # Scenario 1: Account Manager creating account for customer
        am_token = auth_handler.create_token(
            user_id="am_001",
            username="account.manager",
            roles=["account_manager"],
            permissions=[],
            expires_in=3600
        )
        
        am_context = auth_handler.extract_user_context(am_token)
        
        # Account manager can create accounts and read account data
        assert PermissionChecker.can_create_account(am_context, "customer_123")
        assert PermissionChecker.can_access_account(am_context, "customer_123")
        
        # Scenario 2: Customer Service accessing customer data
        cs_token = auth_handler.create_token(
            user_id="cs_001",
            username="customer.service",
            roles=["customer_service"],
            permissions=[],
            expires_in=3600
        )
        
        cs_context = auth_handler.extract_user_context(cs_token)
        
        # Customer service can read accounts and transactions but not create
        assert not PermissionChecker.can_create_account(cs_context, "customer_123")
        assert PermissionChecker.can_access_account(cs_context, "customer_123")
        assert PermissionChecker.has_permission(cs_context, Permission.TRANSACTION_READ)
        
        # Scenario 3: Read-only user accessing data
        ro_token = auth_handler.create_token(
            user_id="ro_001",
            username="readonly.user",
            roles=["readonly_user"],
            permissions=[],
            expires_in=3600
        )
        
        ro_context = auth_handler.extract_user_context(ro_token)
        
        # Read-only user can only read, no write operations
        assert not PermissionChecker.can_create_account(ro_context, "customer_123")
        assert PermissionChecker.can_access_account(ro_context, "customer_123")
        assert PermissionChecker.has_permission(ro_context, Permission.ACCOUNT_READ)
        assert not PermissionChecker.has_permission(ro_context, Permission.ACCOUNT_CREATE)