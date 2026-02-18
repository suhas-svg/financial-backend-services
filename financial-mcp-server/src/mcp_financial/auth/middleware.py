"""
Authentication middleware for MCP tool execution.
"""

import logging
import functools
from typing import Callable, Any, Dict, Optional
from mcp.types import TextContent

from .jwt_handler import JWTAuthHandler, UserContext, AuthenticationError
from .permissions import PermissionChecker, Permission

logger = logging.getLogger(__name__)


class AuthenticationMiddleware:
    """Authentication middleware for MCP tools."""
    
    def __init__(self, jwt_handler: JWTAuthHandler):
        self.jwt_handler = jwt_handler
    
    def require_auth(self, required_permission: Optional[Permission] = None):
        """
        Decorator to require authentication for MCP tools.
        
        Args:
            required_permission: Optional permission required to execute the tool
            
        Returns:
            Decorator function
        """
        def decorator(func: Callable) -> Callable:
            @functools.wraps(func)
            async def wrapper(*args, **kwargs) -> list[TextContent]:
                try:
                    # Extract auth_token from kwargs
                    auth_token = kwargs.get('auth_token')
                    if not auth_token:
                        return [TextContent(
                            type="text",
                            text="Error: Authentication token is required"
                        )]
                    
                    # Validate token and extract user context
                    try:
                        user_context = self.jwt_handler.extract_user_context(auth_token)
                        logger.info(f"Authenticated user: {user_context.username} ({user_context.user_id})")
                    except AuthenticationError as e:
                        logger.warning(f"Authentication failed: {str(e)}")
                        return [TextContent(
                            type="text",
                            text=f"Error: Authentication failed - {str(e)}"
                        )]
                    
                    # Check required permission if specified
                    if required_permission:
                        if not PermissionChecker.has_permission(user_context, required_permission):
                            logger.warning(
                                f"User {user_context.username} lacks permission: {required_permission.value}"
                            )
                            return [TextContent(
                                type="text",
                                text=f"Error: Insufficient permissions - {required_permission.value} required"
                            )]
                    
                    # Add user_context to kwargs for the tool function
                    kwargs['user_context'] = user_context
                    
                    # Execute the original function
                    return await func(*args, **kwargs)
                    
                except Exception as e:
                    logger.error(f"Unexpected error in authentication middleware: {str(e)}")
                    return [TextContent(
                        type="text",
                        text=f"Error: Internal server error during authentication"
                    )]
            
            return wrapper
        return decorator
    
    def require_account_access(self, account_owner_param: str = 'owner_id'):
        """
        Decorator to require account access permission.
        
        Args:
            account_owner_param: Parameter name containing the account owner ID
            
        Returns:
            Decorator function
        """
        def decorator(func: Callable) -> Callable:
            @functools.wraps(func)
            async def wrapper(*args, **kwargs) -> list[TextContent]:
                try:
                    # First apply basic authentication
                    auth_token = kwargs.get('auth_token')
                    if not auth_token:
                        return [TextContent(
                            type="text",
                            text="Error: Authentication token is required"
                        )]
                    
                    try:
                        user_context = self.jwt_handler.extract_user_context(auth_token)
                    except AuthenticationError as e:
                        return [TextContent(
                            type="text",
                            text=f"Error: Authentication failed - {str(e)}"
                        )]
                    
                    # Check account access permission
                    account_owner_id = kwargs.get(account_owner_param)
                    if account_owner_id and not PermissionChecker.can_access_account(
                        user_context, account_owner_id
                    ):
                        logger.warning(
                            f"User {user_context.username} denied access to account owned by {account_owner_id}"
                        )
                        return [TextContent(
                            type="text",
                            text="Error: Access denied - insufficient permissions for this account"
                        )]
                    
                    kwargs['user_context'] = user_context
                    return await func(*args, **kwargs)
                    
                except Exception as e:
                    logger.error(f"Unexpected error in account access middleware: {str(e)}")
                    return [TextContent(
                        type="text",
                        text="Error: Internal server error during authorization"
                    )]
            
            return wrapper
        return decorator
    
    def require_transaction_permission(self, account_owner_param: str = 'account_owner_id'):
        """
        Decorator to require transaction permission.
        
        Args:
            account_owner_param: Parameter name containing the account owner ID
            
        Returns:
            Decorator function
        """
        def decorator(func: Callable) -> Callable:
            @functools.wraps(func)
            async def wrapper(*args, **kwargs) -> list[TextContent]:
                try:
                    # First apply basic authentication
                    auth_token = kwargs.get('auth_token')
                    if not auth_token:
                        return [TextContent(
                            type="text",
                            text="Error: Authentication token is required"
                        )]
                    
                    try:
                        user_context = self.jwt_handler.extract_user_context(auth_token)
                    except AuthenticationError as e:
                        return [TextContent(
                            type="text",
                            text=f"Error: Authentication failed - {str(e)}"
                        )]
                    
                    # Check transaction permission
                    account_owner_id = kwargs.get(account_owner_param)
                    transaction_type = kwargs.get('transaction_type', 'UNKNOWN')
                    
                    if account_owner_id and not PermissionChecker.can_perform_transaction(
                        user_context, account_owner_id, transaction_type
                    ):
                        logger.warning(
                            f"User {user_context.username} denied transaction permission for account owned by {account_owner_id}"
                        )
                        return [TextContent(
                            type="text",
                            text="Error: Transaction denied - insufficient permissions for this account"
                        )]
                    
                    kwargs['user_context'] = user_context
                    return await func(*args, **kwargs)
                    
                except Exception as e:
                    logger.error(f"Unexpected error in transaction permission middleware: {str(e)}")
                    return [TextContent(
                        type="text",
                        text="Error: Internal server error during authorization"
                    )]
            
            return wrapper
        return decorator


def create_auth_middleware(secret_key: str) -> AuthenticationMiddleware:
    """
    Create authentication middleware instance.
    
    Args:
        secret_key: JWT secret key
        
    Returns:
        AuthenticationMiddleware instance
    """
    jwt_handler = JWTAuthHandler(secret_key)
    return AuthenticationMiddleware(jwt_handler)


# Convenience functions for common authentication patterns
def require_auth(required_permission: Optional[Permission] = None):
    """
    Convenience decorator for requiring authentication.
    Note: This requires the middleware to be set up in the application context.
    """
    def decorator(func: Callable) -> Callable:
        # This will be replaced with actual middleware instance during app initialization
        return func
    return decorator


def require_account_access(account_owner_param: str = 'owner_id'):
    """
    Convenience decorator for requiring account access.
    Note: This requires the middleware to be set up in the application context.
    """
    def decorator(func: Callable) -> Callable:
        # This will be replaced with actual middleware instance during app initialization
        return func
    return decorator


def require_transaction_permission(account_owner_param: str = 'account_owner_id'):
    """
    Convenience decorator for requiring transaction permission.
    Note: This requires the middleware to be set up in the application context.
    """
    def decorator(func: Callable) -> Callable:
        # This will be replaced with actual middleware instance during app initialization
        return func
    return decorator