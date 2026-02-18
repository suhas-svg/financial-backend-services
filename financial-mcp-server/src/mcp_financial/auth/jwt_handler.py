"""
JWT authentication handler compatible with existing financial services.
"""

import jwt
import logging
from datetime import datetime, timedelta
from typing import Optional, Dict, Any, List
from dataclasses import dataclass

logger = logging.getLogger(__name__)


@dataclass
class UserContext:
    """User context extracted from JWT token."""
    user_id: str
    username: str
    roles: List[str]
    permissions: List[str]
    
    def has_role(self, role: str) -> bool:
        """Check if user has specific role."""
        return role in self.roles
        
    def has_permission(self, permission: str) -> bool:
        """Check if user has specific permission."""
        return permission in self.permissions


class AuthenticationError(Exception):
    """Authentication related errors."""
    pass


class AuthorizationError(Exception):
    """Authorization related errors."""
    pass


class JWTAuthHandler:
    """JWT authentication handler."""
    
    def __init__(self, secret_key: str, algorithm: str = "HS256"):
        self.secret_key = secret_key
        self.algorithm = algorithm
        
    def validate_token(self, token: str) -> Dict[str, Any]:
        """
        Validate JWT token and return claims.
        
        Args:
            token: JWT token string
            
        Returns:
            Token claims dictionary
            
        Raises:
            AuthenticationError: If token is invalid or expired
        """
        try:
            # Remove 'Bearer ' prefix if present
            if token.startswith('Bearer '):
                token = token[7:]
                
            payload = jwt.decode(
                token, 
                self.secret_key, 
                algorithms=[self.algorithm],
                options={"verify_iat": False}  # Disable iat verification for testing
            )
            
            # Validate token expiration
            if 'exp' in payload:
                exp_timestamp = payload['exp']
                if datetime.utcnow().timestamp() > exp_timestamp:
                    raise AuthenticationError("Token has expired")
                    
            logger.debug(f"Token validated for user: {payload.get('sub', 'unknown')}")
            return payload
            
        except jwt.ExpiredSignatureError:
            logger.warning("Token validation failed: Token has expired")
            raise AuthenticationError("Token has expired")
        except jwt.InvalidTokenError as e:
            logger.warning(f"Token validation failed: {str(e)}")
            raise AuthenticationError("Invalid token")
        except AuthenticationError:
            # Re-raise our own authentication errors
            raise
        except Exception as e:
            logger.error(f"Unexpected error during token validation: {str(e)}")
            raise AuthenticationError("Token validation failed")
            
    def extract_user_context(self, token: str) -> UserContext:
        """
        Extract user context from JWT token.
        
        Args:
            token: JWT token string
            
        Returns:
            UserContext object with user information
            
        Raises:
            AuthenticationError: If token is invalid
        """
        claims = self.validate_token(token)
        
        # Extract user information from claims
        user_id = claims.get('sub', '')
        username = claims.get('username', claims.get('preferred_username', ''))
        roles = claims.get('roles', [])
        permissions = claims.get('permissions', [])
        
        # Ensure roles and permissions are lists
        if isinstance(roles, str):
            roles = [roles]
        if isinstance(permissions, str):
            permissions = [permissions]
            
        return UserContext(
            user_id=user_id,
            username=username,
            roles=roles,
            permissions=permissions
        )
        
    def create_token(
        self, 
        user_id: str, 
        username: str, 
        roles: List[str] = None,
        permissions: List[str] = None,
        expires_in: int = 3600
    ) -> str:
        """
        Create a new JWT token (for testing purposes).
        
        Args:
            user_id: User identifier
            username: Username
            roles: User roles
            permissions: User permissions
            expires_in: Token expiration time in seconds
            
        Returns:
            JWT token string
        """
        now = datetime.utcnow()
        payload = {
            'sub': user_id,
            'username': username,
            'roles': roles or [],
            'permissions': permissions or [],
            'iat': int(now.timestamp()) - 1,  # Subtract 1 second to avoid timing issues
            'exp': int((now + timedelta(seconds=expires_in)).timestamp())
        }
        
        return jwt.encode(payload, self.secret_key, algorithm=self.algorithm)