"""
Custom exceptions for MCP Financial Server.
"""

from .base import (
    MCPFinancialError,
    ValidationError,
    AuthenticationError,
    AuthorizationError,
    ServiceError,
    CircuitBreakerError,
    RateLimitError
)

from .handlers import (
    ErrorHandler,
    ErrorContext,
    ErrorCategory,
    ErrorSeverity
)

__all__ = [
    "MCPFinancialError",
    "ValidationError", 
    "AuthenticationError",
    "AuthorizationError",
    "ServiceError",
    "CircuitBreakerError",
    "RateLimitError",
    "ErrorHandler",
    "ErrorContext",
    "ErrorCategory",
    "ErrorSeverity"
]