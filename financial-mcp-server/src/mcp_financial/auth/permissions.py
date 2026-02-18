"""
Role-based access control and permissions management.
"""

import logging
from typing import List, Dict, Any, Optional
from enum import Enum

from .jwt_handler import UserContext

logger = logging.getLogger(__name__)


class Permission(Enum):
    """Available permissions in the system."""
    
    # Account permissions
    ACCOUNT_CREATE = "account:create"
    ACCOUNT_READ = "account:read"
    ACCOUNT_UPDATE = "account:update"
    ACCOUNT_DELETE = "account:delete"
    ACCOUNT_BALANCE_UPDATE = "account:balance:update"
    
    # Transaction permissions
    TRANSACTION_CREATE = "transaction:create"
    TRANSACTION_READ = "transaction:read"
    TRANSACTION_UPDATE = "transaction:update"
    TRANSACTION_DELETE = "transaction:delete"
    TRANSACTION_REVERSE = "transaction:reverse"
    
    # Query permissions
    QUERY_TRANSACTION_HISTORY = "query:transaction:history"
    QUERY_ACCOUNT_ANALYTICS = "query:account:analytics"
    QUERY_TRANSACTION_SEARCH = "query:transaction:search"
    
    # Admin permissions
    ADMIN_SYSTEM_STATUS = "admin:system:status"
    ADMIN_METRICS = "admin:metrics"
    ADMIN_HEALTH_CHECK = "admin:health"


class Role(Enum):
    """Available roles in the system."""
    
    ADMIN = "admin"
    USER = "user"
    INTERNAL_SERVICE = "internal_service"
    FINANCIAL_OFFICER = "financial_officer"
    ACCOUNT_MANAGER = "account_manager"
    CUSTOMER_SERVICE = "customer_service"
    READONLY_USER = "readonly_user"
    CUSTOMER = "customer"


# Role to permissions mapping
ROLE_PERMISSIONS: Dict[Role, List[Permission]] = {
    Role.ADMIN: [
        # All permissions
        Permission.ACCOUNT_CREATE,
        Permission.ACCOUNT_READ,
        Permission.ACCOUNT_UPDATE,
        Permission.ACCOUNT_DELETE,
        Permission.ACCOUNT_BALANCE_UPDATE,
        Permission.TRANSACTION_CREATE,
        Permission.TRANSACTION_READ,
        Permission.TRANSACTION_UPDATE,
        Permission.TRANSACTION_DELETE,
        Permission.TRANSACTION_REVERSE,
        Permission.QUERY_TRANSACTION_HISTORY,
        Permission.QUERY_ACCOUNT_ANALYTICS,
        Permission.QUERY_TRANSACTION_SEARCH,
        Permission.ADMIN_SYSTEM_STATUS,
        Permission.ADMIN_METRICS,
        Permission.ADMIN_HEALTH_CHECK,
    ],
    Role.INTERNAL_SERVICE: [
        Permission.ACCOUNT_CREATE,
        Permission.ACCOUNT_READ,
        Permission.ACCOUNT_UPDATE,
        Permission.ACCOUNT_DELETE,
        Permission.ACCOUNT_BALANCE_UPDATE,
        Permission.TRANSACTION_CREATE,
        Permission.TRANSACTION_READ,
        Permission.TRANSACTION_UPDATE,
        Permission.TRANSACTION_DELETE,
        Permission.TRANSACTION_REVERSE,
        Permission.QUERY_TRANSACTION_HISTORY,
        Permission.QUERY_ACCOUNT_ANALYTICS,
        Permission.QUERY_TRANSACTION_SEARCH,
        Permission.ADMIN_SYSTEM_STATUS,
        Permission.ADMIN_METRICS,
        Permission.ADMIN_HEALTH_CHECK,
    ],
    Role.USER: [
        Permission.ACCOUNT_CREATE,
        Permission.ACCOUNT_READ,
        Permission.ACCOUNT_UPDATE,
        Permission.ACCOUNT_DELETE,
        Permission.ACCOUNT_BALANCE_UPDATE,
        Permission.TRANSACTION_CREATE,
        Permission.TRANSACTION_READ,
        Permission.TRANSACTION_REVERSE,
        Permission.QUERY_TRANSACTION_HISTORY,
        Permission.QUERY_ACCOUNT_ANALYTICS,
        Permission.QUERY_TRANSACTION_SEARCH,
    ],
    Role.FINANCIAL_OFFICER: [
        Permission.ACCOUNT_CREATE,
        Permission.ACCOUNT_READ,
        Permission.ACCOUNT_UPDATE,
        Permission.ACCOUNT_BALANCE_UPDATE,
        Permission.TRANSACTION_CREATE,
        Permission.TRANSACTION_READ,
        Permission.TRANSACTION_REVERSE,
        Permission.QUERY_TRANSACTION_HISTORY,
        Permission.QUERY_ACCOUNT_ANALYTICS,
        Permission.QUERY_TRANSACTION_SEARCH,
    ],
    Role.ACCOUNT_MANAGER: [
        Permission.ACCOUNT_CREATE,
        Permission.ACCOUNT_READ,
        Permission.ACCOUNT_UPDATE,
        Permission.TRANSACTION_READ,
        Permission.QUERY_TRANSACTION_HISTORY,
        Permission.QUERY_ACCOUNT_ANALYTICS,
    ],
    Role.CUSTOMER_SERVICE: [
        Permission.ACCOUNT_READ,
        Permission.TRANSACTION_READ,
        Permission.QUERY_TRANSACTION_HISTORY,
        Permission.QUERY_TRANSACTION_SEARCH,
    ],
    Role.READONLY_USER: [
        Permission.ACCOUNT_READ,
        Permission.TRANSACTION_READ,
        Permission.QUERY_TRANSACTION_HISTORY,
    ],
    Role.CUSTOMER: [
        Permission.ACCOUNT_READ,  # Own accounts only
        Permission.TRANSACTION_READ,  # Own transactions only
        Permission.QUERY_TRANSACTION_HISTORY,  # Own history only
    ],
}


class PermissionChecker:
    """Permission checking utility."""

    @staticmethod
    def _resolve_role(role_str: str) -> Optional[Role]:
        normalized = (role_str or "").strip()
        if not normalized:
            return None

        # Normalize Spring Security roles (e.g. ROLE_ADMIN, ROLE_USER)
        if normalized.upper().startswith("ROLE_"):
            normalized = normalized[5:]
        normalized = normalized.lower()

        try:
            return Role(normalized)
        except ValueError:
            logger.warning(f"Unknown role: {role_str}")
            return None
    
    @staticmethod
    def has_permission(user_context: UserContext, permission: Permission) -> bool:
        """
        Check if user has specific permission.
        
        Args:
            user_context: User context from JWT
            permission: Required permission
            
        Returns:
            True if user has permission, False otherwise
        """
        # Check direct permissions
        if permission.value in user_context.permissions:
            return True

        resolved_roles = []
        for role_str in user_context.roles:
            role = PermissionChecker._resolve_role(role_str)
            if role:
                resolved_roles.append(role)

        # Admin/internal service bypass
        if Role.ADMIN in resolved_roles or Role.INTERNAL_SERVICE in resolved_roles:
            return True

        # Check role-based permissions
        for role in resolved_roles:
            if permission in ROLE_PERMISSIONS.get(role, []):
                return True

        return False
        
    @staticmethod
    def can_create_account(user_context: UserContext, owner_id: str = None) -> bool:
        """Check if user can create accounts."""
        if not PermissionChecker.has_permission(user_context, Permission.ACCOUNT_CREATE):
            return False

        role_names = {r.value for r in [PermissionChecker._resolve_role(role) for role in user_context.roles] if r}
        # End users can only create their own accounts
        if Role.USER.value in role_names or Role.CUSTOMER.value in role_names:
            return owner_id is None or owner_id == user_context.user_id

        return True
        
    @staticmethod
    def can_access_account(user_context: UserContext, account_owner_id: str) -> bool:
        """Check if user can access specific account."""
        if not PermissionChecker.has_permission(user_context, Permission.ACCOUNT_READ):
            return False

        role_names = {r.value for r in [PermissionChecker._resolve_role(role) for role in user_context.roles] if r}

        if account_owner_id is None:
            return True

        # End users can only access their own accounts
        if Role.USER.value in role_names or Role.CUSTOMER.value in role_names:
            return account_owner_id == user_context.user_id

        # Other roles can access any account
        return True
        
    @staticmethod
    def can_perform_transaction(
        user_context: UserContext, 
        account_owner_id: str,
        transaction_type: str
    ) -> bool:
        """Check if user can perform transaction on account."""
        if not PermissionChecker.has_permission(user_context, Permission.TRANSACTION_CREATE):
            return False

        role_names = {r.value for r in [PermissionChecker._resolve_role(role) for role in user_context.roles] if r}

        if account_owner_id is None:
            return True

        # End users can only perform transactions on their own accounts
        if Role.USER.value in role_names or Role.CUSTOMER.value in role_names:
            return account_owner_id == user_context.user_id

        return True
        
    @staticmethod
    def can_reverse_transaction(user_context: UserContext) -> bool:
        """Check if user can reverse transactions."""
        return PermissionChecker.has_permission(user_context, Permission.TRANSACTION_REVERSE)
        
    @staticmethod
    def can_access_analytics(user_context: UserContext, account_owner_id: str = None) -> bool:
        """Check if user can access analytics data."""
        if not PermissionChecker.has_permission(user_context, Permission.QUERY_ACCOUNT_ANALYTICS):
            return False

        role_names = {r.value for r in [PermissionChecker._resolve_role(role) for role in user_context.roles] if r}
        # End users can only access their own analytics
        if (Role.USER.value in role_names or Role.CUSTOMER.value in role_names) and account_owner_id:
            return account_owner_id == user_context.user_id

        return True

    @staticmethod
    def can_modify_account(user_context: UserContext, account_owner_id: str) -> bool:
        """Compatibility helper used by enhanced account tools."""
        if not PermissionChecker.has_permission(user_context, Permission.ACCOUNT_UPDATE):
            return False
        return PermissionChecker.can_access_account(user_context, account_owner_id)
