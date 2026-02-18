"""Authentication and authorization module."""

from .jwt_handler import JWTAuthHandler, UserContext, AuthenticationError, AuthorizationError
from .permissions import (
    Permission,
    Role,
    ROLE_PERMISSIONS,
    PermissionChecker
)
from .middleware import (
    AuthenticationMiddleware,
    create_auth_middleware,
    require_auth,
    require_account_access,
    require_transaction_permission
)

__all__ = [
    # JWT Handler
    'JWTAuthHandler',
    'UserContext',
    'AuthenticationError',
    'AuthorizationError',
    
    # Permissions
    'Permission',
    'Role',
    'ROLE_PERMISSIONS',
    'PermissionChecker',
    
    # Middleware
    'AuthenticationMiddleware',
    'create_auth_middleware',
    'require_auth',
    'require_account_access',
    'require_transaction_permission',
]