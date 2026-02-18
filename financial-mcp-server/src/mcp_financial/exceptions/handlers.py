"""
Error handling utilities and context management.
"""

import logging
import traceback
import uuid
from enum import Enum
from typing import Dict, Any, Optional, List, Type, Callable
from datetime import datetime
from contextlib import contextmanager

from mcp.types import TextContent

from .base import MCPFinancialError, ValidationError, AuthenticationError, ServiceError
from ..models.responses import MCPErrorResponse
from ..utils.metrics import error_counter, error_duration

logger = logging.getLogger(__name__)


class ErrorCategory(Enum):
    """Error categories for classification."""
    VALIDATION = "validation"
    AUTHENTICATION = "authentication"
    AUTHORIZATION = "authorization"
    SERVICE = "service"
    BUSINESS_RULE = "business_rule"
    DATA_INTEGRITY = "data_integrity"
    TIMEOUT = "timeout"
    CIRCUIT_BREAKER = "circuit_breaker"
    RATE_LIMIT = "rate_limit"
    INTERNAL = "internal"


class ErrorSeverity(Enum):
    """Error severity levels."""
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class ErrorContext:
    """Context information for error handling."""
    
    def __init__(
        self,
        operation: str,
        user_id: Optional[str] = None,
        request_id: Optional[str] = None,
        additional_context: Optional[Dict[str, Any]] = None
    ):
        self.operation = operation
        self.user_id = user_id
        self.request_id = request_id or str(uuid.uuid4())
        self.additional_context = additional_context or {}
        self.timestamp = datetime.utcnow()
        
    def to_dict(self) -> Dict[str, Any]:
        """Convert context to dictionary."""
        return {
            "operation": self.operation,
            "user_id": self.user_id,
            "request_id": self.request_id,
            "timestamp": self.timestamp.isoformat(),
            "additional_context": self.additional_context
        }


class ErrorHandler:
    """Centralized error handling and logging."""
    
    def __init__(self):
        self.error_mappings: Dict[Type[Exception], ErrorCategory] = {
            ValidationError: ErrorCategory.VALIDATION,
            AuthenticationError: ErrorCategory.AUTHENTICATION,
            ServiceError: ErrorCategory.SERVICE,
            # Add more mappings as needed
        }
        
    def handle_error(
        self,
        error: Exception,
        context: ErrorContext,
        include_traceback: bool = False
    ) -> MCPErrorResponse:
        """
        Handle and log error, returning structured error response.
        
        Args:
            error: The exception that occurred
            context: Error context information
            include_traceback: Whether to include traceback in details
            
        Returns:
            Structured error response
        """
        # Determine error category and severity
        category = self._get_error_category(error)
        severity = self._get_error_severity(error, category)
        
        # Create error response
        if isinstance(error, MCPFinancialError):
            error_response = MCPErrorResponse(
                error_code=error.error_code,
                error_message=error.message,
                details=error.details,
                request_id=context.request_id
            )
        else:
            error_response = MCPErrorResponse(
                error_code="INTERNAL_ERROR",
                error_message=str(error),
                details={"category": category.value},
                request_id=context.request_id
            )
            
        # Add context information to details
        error_response.details.update({
            "operation": context.operation,
            "severity": severity.value,
            "category": category.value
        })
        
        if include_traceback:
            error_response.details["traceback"] = traceback.format_exc()
            
        # Log error with appropriate level
        self._log_error(error, context, category, severity)
        
        # Update metrics
        error_counter.labels(
            operation=context.operation,
            error_type=type(error).__name__,
            category=category.value,
            severity=severity.value
        ).inc()
        
        return error_response
        
    def _get_error_category(self, error: Exception) -> ErrorCategory:
        """Determine error category from exception type."""
        error_type = type(error)
        
        # Check direct mappings
        if error_type in self.error_mappings:
            return self.error_mappings[error_type]
            
        # Check inheritance
        for exception_type, category in self.error_mappings.items():
            if isinstance(error, exception_type):
                return category
                
        # Default to internal error
        return ErrorCategory.INTERNAL
        
    def _get_error_severity(self, error: Exception, category: ErrorCategory) -> ErrorSeverity:
        """Determine error severity based on type and category."""
        if isinstance(error, (ValidationError, AuthenticationError)):
            return ErrorSeverity.LOW
        elif isinstance(error, ServiceError):
            return ErrorSeverity.MEDIUM
        elif category == ErrorCategory.CIRCUIT_BREAKER:
            return ErrorSeverity.HIGH
        elif category == ErrorCategory.INTERNAL:
            return ErrorSeverity.CRITICAL
        else:
            return ErrorSeverity.MEDIUM
            
    def _log_error(
        self,
        error: Exception,
        context: ErrorContext,
        category: ErrorCategory,
        severity: ErrorSeverity
    ) -> None:
        """Log error with structured information."""
        log_data = {
            "error_type": type(error).__name__,
            "error_message": str(error),
            "category": category.value,
            "severity": severity.value,
            **context.to_dict()
        }
        
        # Log with appropriate level based on severity
        if severity == ErrorSeverity.CRITICAL:
            logger.critical("Critical error occurred", extra=log_data, exc_info=True)
        elif severity == ErrorSeverity.HIGH:
            logger.error("High severity error occurred", extra=log_data, exc_info=True)
        elif severity == ErrorSeverity.MEDIUM:
            logger.warning("Medium severity error occurred", extra=log_data)
        else:
            logger.info("Low severity error occurred", extra=log_data)


class ValidationErrorCollector:
    """Utility for collecting and managing validation errors."""
    
    def __init__(self):
        self.errors: List[Dict[str, Any]] = []
        
    def add_error(
        self,
        field: str,
        message: str,
        value: Optional[Any] = None,
        error_type: str = "validation_error"
    ) -> None:
        """Add a validation error."""
        error_info = {
            "field": field,
            "message": message,
            "type": error_type
        }
        if value is not None:
            error_info["invalid_value"] = str(value)
            
        self.errors.append(error_info)
        
    def has_errors(self) -> bool:
        """Check if there are any validation errors."""
        return len(self.errors) > 0
        
    def get_errors(self) -> List[Dict[str, Any]]:
        """Get all validation errors."""
        return self.errors.copy()
        
    def raise_if_errors(self, request_id: Optional[str] = None) -> None:
        """Raise ValidationError if there are any errors."""
        if self.has_errors():
            raise ValidationError(
                message=f"Validation failed with {len(self.errors)} error(s)",
                details={"validation_errors": self.errors},
                request_id=request_id
            )


@contextmanager
def error_handling_context(
    operation: str,
    user_id: Optional[str] = None,
    request_id: Optional[str] = None,
    additional_context: Optional[Dict[str, Any]] = None,
    include_traceback: bool = False
):
    """
    Context manager for standardized error handling.
    
    Usage:
        with error_handling_context("create_account", user_id="123") as ctx:
            # Operation code here
            pass
    """
    context = ErrorContext(
        operation=operation,
        user_id=user_id,
        request_id=request_id,
        additional_context=additional_context
    )
    
    error_handler = ErrorHandler()
    
    try:
        yield context
    except Exception as e:
        error_response = error_handler.handle_error(e, context, include_traceback)
        # Re-raise as MCPFinancialError with structured response
        raise MCPFinancialError(
            message=error_response.error_message,
            error_code=error_response.error_code,
            details=error_response.details,
            request_id=context.request_id
        ) from e


def create_error_response(
    error: Exception,
    operation: str,
    request_id: Optional[str] = None,
    user_id: Optional[str] = None
) -> List[TextContent]:
    """
    Create standardized MCP error response.
    
    Args:
        error: The exception that occurred
        operation: Operation name for context
        request_id: Request identifier
        user_id: User identifier
        
    Returns:
        List containing error response as TextContent
    """
    context = ErrorContext(
        operation=operation,
        user_id=user_id,
        request_id=request_id
    )
    
    error_handler = ErrorHandler()
    error_response = error_handler.handle_error(error, context)
    
    return [TextContent(type="text", text=error_response.model_dump_json())]


def safe_execute(
    operation: str,
    func: Callable,
    *args,
    user_id: Optional[str] = None,
    request_id: Optional[str] = None,
    **kwargs
) -> List[TextContent]:
    """
    Safely execute a function with error handling.
    
    Args:
        operation: Operation name for context
        func: Function to execute
        *args: Function arguments
        user_id: User identifier for context
        request_id: Request identifier
        **kwargs: Function keyword arguments
        
    Returns:
        Function result or error response
    """
    try:
        with error_handling_context(operation, user_id, request_id) as ctx:
            return func(*args, **kwargs)
    except MCPFinancialError as e:
        return create_error_response(e, operation, request_id, user_id)
    except Exception as e:
        return create_error_response(e, operation, request_id, user_id)


class CircuitBreakerManager:
    """Enhanced circuit breaker with error handling integration."""
    
    def __init__(
        self,
        failure_threshold: int = 5,
        recovery_timeout: int = 30,
        half_open_max_calls: int = 3
    ):
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.half_open_max_calls = half_open_max_calls
        self.failure_count = 0
        self.last_failure_time: Optional[datetime] = None
        self.state = "CLOSED"  # CLOSED, OPEN, HALF_OPEN
        self.half_open_calls = 0
        
    def call(self, func: Callable, *args, **kwargs):
        """Execute function with circuit breaker protection."""
        if self.state == "OPEN":
            if self._should_attempt_reset():
                self.state = "HALF_OPEN"
                self.half_open_calls = 0
                logger.info("Circuit breaker transitioning to HALF_OPEN")
            else:
                from .base import CircuitBreakerError
                raise CircuitBreakerError("Circuit breaker is OPEN")
                
        if self.state == "HALF_OPEN" and self.half_open_calls >= self.half_open_max_calls:
            from .base import CircuitBreakerError
            raise CircuitBreakerError("Circuit breaker HALF_OPEN call limit exceeded")
            
        try:
            if self.state == "HALF_OPEN":
                self.half_open_calls += 1
                
            result = func(*args, **kwargs)
            self._on_success()
            return result
            
        except Exception as e:
            self._on_failure()
            raise
            
    def _should_attempt_reset(self) -> bool:
        """Check if circuit breaker should attempt reset."""
        if self.last_failure_time is None:
            return True
        return (datetime.utcnow() - self.last_failure_time).total_seconds() > self.recovery_timeout
        
    def _on_success(self):
        """Handle successful call."""
        self.failure_count = 0
        if self.state == "HALF_OPEN":
            self.state = "CLOSED"
            logger.info("Circuit breaker reset to CLOSED")
            
    def _on_failure(self):
        """Handle failed call."""
        self.failure_count += 1
        self.last_failure_time = datetime.utcnow()
        
        if self.failure_count >= self.failure_threshold:
            self.state = "OPEN"
            logger.warning(f"Circuit breaker opened after {self.failure_count} failures")