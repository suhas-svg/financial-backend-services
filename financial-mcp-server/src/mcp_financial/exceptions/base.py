"""
Base exception classes for MCP Financial Server.
"""

from typing import Optional, Dict, Any
from datetime import datetime


class MCPFinancialError(Exception):
    """Base exception for all MCP Financial Server errors."""
    
    def __init__(
        self,
        message: str,
        error_code: str = "UNKNOWN_ERROR",
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        super().__init__(message)
        self.message = message
        self.error_code = error_code
        self.details = details or {}
        self.request_id = request_id
        self.timestamp = datetime.utcnow()
        
    def to_dict(self) -> Dict[str, Any]:
        """Convert exception to dictionary format."""
        return {
            "error_code": self.error_code,
            "error_message": self.message,
            "details": self.details,
            "timestamp": self.timestamp.isoformat(),
            "request_id": self.request_id
        }


class ValidationError(MCPFinancialError):
    """Exception raised for validation errors."""
    
    def __init__(
        self,
        message: str,
        field: Optional[str] = None,
        value: Optional[Any] = None,
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        error_details = details or {}
        if field:
            error_details["field"] = field
        if value is not None:
            error_details["invalid_value"] = str(value)
            
        super().__init__(
            message=message,
            error_code="VALIDATION_ERROR",
            details=error_details,
            request_id=request_id
        )
        self.field = field
        self.value = value


class AuthenticationError(MCPFinancialError):
    """Exception raised for authentication errors."""
    
    def __init__(
        self,
        message: str = "Authentication failed",
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        super().__init__(
            message=message,
            error_code="AUTHENTICATION_ERROR",
            details=details,
            request_id=request_id
        )


class AuthorizationError(MCPFinancialError):
    """Exception raised for authorization errors."""
    
    def __init__(
        self,
        message: str = "Access denied",
        resource: Optional[str] = None,
        action: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        error_details = details or {}
        if resource:
            error_details["resource"] = resource
        if action:
            error_details["action"] = action
            
        super().__init__(
            message=message,
            error_code="AUTHORIZATION_ERROR",
            details=error_details,
            request_id=request_id
        )
        self.resource = resource
        self.action = action


class ServiceError(MCPFinancialError):
    """Exception raised for backend service errors."""
    
    def __init__(
        self,
        message: str,
        service_name: Optional[str] = None,
        status_code: Optional[int] = None,
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        error_details = details or {}
        if service_name:
            error_details["service"] = service_name
        if status_code:
            error_details["status_code"] = status_code
            
        super().__init__(
            message=message,
            error_code="SERVICE_ERROR",
            details=error_details,
            request_id=request_id
        )
        self.service_name = service_name
        self.status_code = status_code


class CircuitBreakerError(MCPFinancialError):
    """Exception raised when circuit breaker is open."""
    
    def __init__(
        self,
        message: str = "Service circuit breaker is open",
        service_name: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        error_details = details or {}
        if service_name:
            error_details["service"] = service_name
            
        super().__init__(
            message=message,
            error_code="CIRCUIT_BREAKER_OPEN",
            details=error_details,
            request_id=request_id
        )
        self.service_name = service_name


class RateLimitError(MCPFinancialError):
    """Exception raised when rate limit is exceeded."""
    
    def __init__(
        self,
        message: str = "Rate limit exceeded",
        limit: Optional[int] = None,
        window: Optional[int] = None,
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        error_details = details or {}
        if limit:
            error_details["rate_limit"] = limit
        if window:
            error_details["window_seconds"] = window
            
        super().__init__(
            message=message,
            error_code="RATE_LIMIT_EXCEEDED",
            details=error_details,
            request_id=request_id
        )
        self.limit = limit
        self.window = window


class BusinessRuleError(MCPFinancialError):
    """Exception raised for business rule violations."""
    
    def __init__(
        self,
        message: str,
        rule: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        error_details = details or {}
        if rule:
            error_details["business_rule"] = rule
            
        super().__init__(
            message=message,
            error_code="BUSINESS_RULE_VIOLATION",
            details=error_details,
            request_id=request_id
        )
        self.rule = rule


class DataIntegrityError(MCPFinancialError):
    """Exception raised for data integrity violations."""
    
    def __init__(
        self,
        message: str,
        entity: Optional[str] = None,
        constraint: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        error_details = details or {}
        if entity:
            error_details["entity"] = entity
        if constraint:
            error_details["constraint"] = constraint
            
        super().__init__(
            message=message,
            error_code="DATA_INTEGRITY_ERROR",
            details=error_details,
            request_id=request_id
        )
        self.entity = entity
        self.constraint = constraint


class TimeoutError(MCPFinancialError):
    """Exception raised for timeout errors."""
    
    def __init__(
        self,
        message: str = "Operation timed out",
        timeout_seconds: Optional[float] = None,
        operation: Optional[str] = None,
        details: Optional[Dict[str, Any]] = None,
        request_id: Optional[str] = None
    ):
        error_details = details or {}
        if timeout_seconds:
            error_details["timeout_seconds"] = timeout_seconds
        if operation:
            error_details["operation"] = operation
            
        super().__init__(
            message=message,
            error_code="TIMEOUT_ERROR",
            details=error_details,
            request_id=request_id
        )
        self.timeout_seconds = timeout_seconds
        self.operation = operation