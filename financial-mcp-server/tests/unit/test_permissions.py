"""
Unit tests for permissions and role-based access control.
"""

import pytest
from mcp_financial.auth.jwt_handler import UserContext
from mcp_financial.auth.permissions import (
    Permission,
    Role,
    ROLE_PERMISSIONS,
    PermissionChecker
)


class TestPermission:
    """Test cases for Permission enum."""
    
    def test_permission_values(self):
        """Test permission enum values."""
        assert Permission.ACCOUNT_CREATE.value == "account:create"
        assert Permission.ACCOUNT_READ.value == "account:read"
        assert Permission.TRANSACTION_CREATE.value == "transaction:create"
        assert Permission.ADMIN_SYSTEM_STATUS.value == "admin:system:status"
    
    def test_all_permissions_defined(self):
        """Test that all expected permissions are defined."""
        expected_permissions = [
            "account:create", "account:read", "account:update", "account:delete",
            "account:balance:update", "transaction:create", "transaction:read",
            "transaction:update", "transaction:delete", "transaction:reverse",
            "query:transaction:history", "query:account:analytics", 
            "query:transaction:search", "admin:system:status", "admin:metrics",
            "admin:health"
        ]
        
        actual_permissions = [p.value for p in Permission]
        
        for expected in expected_permissions:
            assert expected in actual_permissions


class TestRole:
    """Test cases for Role enum."""
    
    def test_role_values(self):
        """Test role enum values."""
        assert Role.ADMIN.value == "admin"
        assert Role.CUSTOMER.value == "customer"
        assert Role.FINANCIAL_OFFICER.value == "financial_officer"
    
    def test_all_roles_defined(self):
        """Test that all expected roles are defined."""
        expected_roles = [
            "admin", "financial_officer", "account_manager", 
            "customer_service", "readonly_user", "customer"
        ]
        
        actual_roles = [r.value for r in Role]
        
        for expected in expected_roles:
            assert expected in actual_roles


class TestRolePermissions:
    """Test cases for role-permission mappings."""
    
    def test_admin_has_all_permissions(self):
        """Test that admin role has all permissions."""
        admin_permissions = ROLE_PERMISSIONS[Role.ADMIN]
        all_permissions = list(Permission)
        
        assert len(admin_permissions) == len(all_permissions)
        for permission in all_permissions:
            assert permission in admin_permissions
    
    def test_customer_has_limited_permissions(self):
        """Test that customer role has limited permissions."""
        customer_permissions = ROLE_PERMISSIONS[Role.CUSTOMER]
        
        # Customer should have read-only permissions
        assert Permission.ACCOUNT_READ in customer_permissions
        assert Permission.TRANSACTION_READ in customer_permissions
        assert Permission.QUERY_TRANSACTION_HISTORY in customer_permissions
        
        # Customer should not have admin permissions
        assert Permission.ADMIN_SYSTEM_STATUS not in customer_permissions
        assert Permission.ACCOUNT_DELETE not in customer_permissions
        assert Permission.TRANSACTION_REVERSE not in customer_permissions
    
    def test_financial_officer_permissions(self):
        """Test financial officer permissions."""
        fo_permissions = ROLE_PERMISSIONS[Role.FINANCIAL_OFFICER]
        
        # Should have most financial operations
        assert Permission.ACCOUNT_CREATE in fo_permissions
        assert Permission.TRANSACTION_CREATE in fo_permissions
        assert Permission.TRANSACTION_REVERSE in fo_permissions
        assert Permission.QUERY_ACCOUNT_ANALYTICS in fo_permissions
        
        # Should not have admin system permissions
        assert Permission.ADMIN_SYSTEM_STATUS not in fo_permissions
    
    def test_readonly_user_permissions(self):
        """Test readonly user permissions."""
        readonly_permissions = ROLE_PERMISSIONS[Role.READONLY_USER]
        
        # Should only have read permissions
        assert Permission.ACCOUNT_READ in readonly_permissions
        assert Permission.TRANSACTION_READ in readonly_permissions
        assert Permission.QUERY_TRANSACTION_HISTORY in readonly_permissions
        
        # Should not have write permissions
        assert Permission.ACCOUNT_CREATE not in readonly_permissions
        assert Permission.TRANSACTION_CREATE not in readonly_permissions


class TestPermissionChecker:
    """Test cases for PermissionChecker."""
    
    @pytest.fixture
    def admin_user(self):
        """Create admin user context."""
        return UserContext(
            user_id="admin1",
            username="admin",
            roles=["admin"],
            permissions=[]
        )
    
    @pytest.fixture
    def customer_user(self):
        """Create customer user context."""
        return UserContext(
            user_id="customer1",
            username="customer",
            roles=["customer"],
            permissions=[]
        )
    
    @pytest.fixture
    def financial_officer_user(self):
        """Create financial officer user context."""
        return UserContext(
            user_id="fo1",
            username="financial_officer",
            roles=["financial_officer"],
            permissions=[]
        )
    
    @pytest.fixture
    def user_with_direct_permissions(self):
        """Create user with direct permissions."""
        return UserContext(
            user_id="user1",
            username="user",
            roles=[],
            permissions=["account:create", "transaction:read"]
        )
    
    def test_has_permission_via_role(self, admin_user):
        """Test permission check via role."""
        assert PermissionChecker.has_permission(admin_user, Permission.ACCOUNT_CREATE) is True
        assert PermissionChecker.has_permission(admin_user, Permission.ADMIN_SYSTEM_STATUS) is True
    
    def test_has_permission_via_direct_permission(self, user_with_direct_permissions):
        """Test permission check via direct permission."""
        assert PermissionChecker.has_permission(
            user_with_direct_permissions, Permission.ACCOUNT_CREATE
        ) is True
        assert PermissionChecker.has_permission(
            user_with_direct_permissions, Permission.TRANSACTION_READ
        ) is True
        assert PermissionChecker.has_permission(
            user_with_direct_permissions, Permission.ADMIN_SYSTEM_STATUS
        ) is False
    
    def test_has_permission_no_access(self, customer_user):
        """Test permission check with no access."""
        assert PermissionChecker.has_permission(
            customer_user, Permission.ADMIN_SYSTEM_STATUS
        ) is False
        assert PermissionChecker.has_permission(
            customer_user, Permission.ACCOUNT_DELETE
        ) is False
    
    def test_has_permission_unknown_role(self):
        """Test permission check with unknown role."""
        user = UserContext(
            user_id="user1",
            username="user",
            roles=["unknown_role"],
            permissions=[]
        )
        
        assert PermissionChecker.has_permission(user, Permission.ACCOUNT_READ) is False
    
    def test_can_create_account_admin(self, admin_user):
        """Test account creation permission for admin."""
        assert PermissionChecker.can_create_account(admin_user) is True
        assert PermissionChecker.can_create_account(admin_user, "any_user") is True
    
    def test_can_create_account_customer_own(self, customer_user):
        """Test account creation permission for customer (own account)."""
        # Customer role doesn't have ACCOUNT_CREATE permission by default
        # This is correct business logic - customers don't typically create their own accounts
        assert PermissionChecker.can_create_account(customer_user, "customer1") is False
        assert PermissionChecker.can_create_account(customer_user, None) is False
    
    def test_can_create_account_customer_other(self, customer_user):
        """Test account creation permission for customer (other's account)."""
        # Customer should not be able to create account for others (and doesn't have permission anyway)
        assert PermissionChecker.can_create_account(customer_user, "other_user") is False
    
    def test_can_create_account_no_permission(self):
        """Test account creation with no permission."""
        user = UserContext(
            user_id="user1",
            username="user",
            roles=["readonly_user"],
            permissions=[]
        )
        
        assert PermissionChecker.can_create_account(user) is False
    
    def test_can_access_account_admin(self, admin_user):
        """Test account access permission for admin."""
        assert PermissionChecker.can_access_account(admin_user, "any_user") is True
    
    def test_can_access_account_customer_own(self, customer_user):
        """Test account access permission for customer (own account)."""
        assert PermissionChecker.can_access_account(customer_user, "customer1") is True
    
    def test_can_access_account_customer_other(self, customer_user):
        """Test account access permission for customer (other's account)."""
        assert PermissionChecker.can_access_account(customer_user, "other_user") is False
    
    def test_can_access_account_financial_officer(self, financial_officer_user):
        """Test account access permission for financial officer."""
        assert PermissionChecker.can_access_account(financial_officer_user, "any_user") is True
    
    def test_can_perform_transaction_admin(self, admin_user):
        """Test transaction permission for admin."""
        assert PermissionChecker.can_perform_transaction(
            admin_user, "any_user", "DEPOSIT"
        ) is True
    
    def test_can_perform_transaction_customer_own(self, customer_user):
        """Test transaction permission for customer (own account)."""
        # Note: Customer role doesn't have TRANSACTION_CREATE permission by default
        # This test assumes the customer has been granted the permission
        customer_with_transaction_perm = UserContext(
            user_id="customer1",
            username="customer",
            roles=["customer"],
            permissions=["transaction:create"]
        )
        
        assert PermissionChecker.can_perform_transaction(
            customer_with_transaction_perm, "customer1", "DEPOSIT"
        ) is True
    
    def test_can_perform_transaction_customer_other(self, customer_user):
        """Test transaction permission for customer (other's account)."""
        customer_with_transaction_perm = UserContext(
            user_id="customer1",
            username="customer",
            roles=["customer"],
            permissions=["transaction:create"]
        )
        
        assert PermissionChecker.can_perform_transaction(
            customer_with_transaction_perm, "other_user", "DEPOSIT"
        ) is False
    
    def test_can_reverse_transaction_admin(self, admin_user):
        """Test transaction reversal permission for admin."""
        assert PermissionChecker.can_reverse_transaction(admin_user) is True
    
    def test_can_reverse_transaction_financial_officer(self, financial_officer_user):
        """Test transaction reversal permission for financial officer."""
        assert PermissionChecker.can_reverse_transaction(financial_officer_user) is True
    
    def test_can_reverse_transaction_customer(self, customer_user):
        """Test transaction reversal permission for customer."""
        assert PermissionChecker.can_reverse_transaction(customer_user) is False
    
    def test_can_access_analytics_admin(self, admin_user):
        """Test analytics access permission for admin."""
        assert PermissionChecker.can_access_analytics(admin_user) is True
        assert PermissionChecker.can_access_analytics(admin_user, "any_user") is True
    
    def test_can_access_analytics_customer_own(self, customer_user):
        """Test analytics access permission for customer (own data)."""
        # Customer role doesn't have analytics permission by default
        customer_with_analytics = UserContext(
            user_id="customer1",
            username="customer",
            roles=["customer"],
            permissions=["query:account:analytics"]
        )
        
        assert PermissionChecker.can_access_analytics(
            customer_with_analytics, "customer1"
        ) is True
    
    def test_can_access_analytics_customer_other(self, customer_user):
        """Test analytics access permission for customer (other's data)."""
        customer_with_analytics = UserContext(
            user_id="customer1",
            username="customer",
            roles=["customer"],
            permissions=["query:account:analytics"]
        )
        
        assert PermissionChecker.can_access_analytics(
            customer_with_analytics, "other_user"
        ) is False
    
    def test_can_access_analytics_no_permission(self):
        """Test analytics access with no permission."""
        user = UserContext(
            user_id="user1",
            username="user",
            roles=["readonly_user"],
            permissions=[]
        )
        
        assert PermissionChecker.can_access_analytics(user) is False