"""
Enhanced error logging utilities for MCP Financial Server.
"""

import logging
import json
import traceback
import sys
from typing import Dict, Any, Optional, List
from datetime import datetime
from contextlib import contextmanager

from ..exceptions.base import MCPFinancialError
from ..exceptions.handlers import ErrorCategory, ErrorSeverity


class StructuredErrorLogger:
    """Structured error logger with JSON formatting."""
    
    def __init__(self, logger_name: str = "mcp_financial"):
        self.logger = logging.getLogger(logger_name)
        self._setup_structured_logging()
        
    def _setup_structured_logging(self):
        """Setup structured JSON logging format."""
        if not self.logger.handlers:
            handler = logging.StreamHandler(sys.stdout)
            formatter = StructuredFormatter()
            handler.setFormatter(formatter)
            self.logger.addHandler(handler)
            self.logger.setLevel(logging.INFO)
            
    def log_error(
        self,
        error: Exception,
        operation: str,
        user_id: Optional[str] = None,
        request_id: Optional[str] = None,
        additional_context: Optional[Dict[str, Any]] = None,
        include_traceback: bool = True
    ) -> None:
        """
        Log error with structured information.
        
        Args:
            error: The exception that occurred
            operation: Operation name where error occurred
            user_id: User identifier
            request_id: Request identifier
            additional_context: Additional context information
            include_traceback: Whether to include full traceback
        """
        log_data = {
            "event_type": "error",
            "error_type": type(error).__name__,
            "error_message": str(error),
            "operation": operation,
            "timestamp": datetime.utcnow().isoformat(),
            "user_id": user_id,
            "request_id": request_id,
        }
        
        # Add MCP Financial Error specific information
        if isinstance(error, MCPFinancialError):
            log_data.update({
                "error_code": error.error_code,
                "error_details": error.details,
                "error_timestamp": error.timestamp.isoformat()
            })
            
        # Add additional context
        if additional_context:
            log_data["context"] = additional_context
            
        # Add traceback if requested
        if include_traceback:
            log_data["traceback"] = traceback.format_exc()
            
        # Determine log level based on error type
        log_level = self._get_log_level(error)
        
        # Log with appropriate level
        self.logger.log(log_level, "Error occurred", extra={"structured_data": log_data})
        
    def log_validation_error(
        self,
        field_errors: List[Dict[str, Any]],
        operation: str,
        user_id: Optional[str] = None,
        request_id: Optional[str] = None
    ) -> None:
        """Log validation errors with field-level details."""
        log_data = {
            "event_type": "validation_error",
            "operation": operation,
            "timestamp": datetime.utcnow().isoformat(),
            "user_id": user_id,
            "request_id": request_id,
            "validation_errors": field_errors,
            "error_count": len(field_errors)
        }
        
        self.logger.warning("Validation errors occurred", extra={"structured_data": log_data})
        
    def log_security_event(
        self,
        event_type: str,
        description: str,
        user_id: Optional[str] = None,
        ip_address: Optional[str] = None,
        additional_data: Optional[Dict[str, Any]] = None
    ) -> None:
        """Log security-related events."""
        log_data = {
            "event_type": "security_event",
            "security_event_type": event_type,
            "description": description,
            "timestamp": datetime.utcnow().isoformat(),
            "user_id": user_id,
            "ip_address": ip_address,
            "severity": "high"
        }
        
        if additional_data:
            log_data["additional_data"] = additional_data
            
        self.logger.error("Security event detected", extra={"structured_data": log_data})
        
    def log_performance_issue(
        self,
        operation: str,
        duration_ms: float,
        threshold_ms: float,
        additional_metrics: Optional[Dict[str, Any]] = None
    ) -> None:
        """Log performance issues."""
        log_data = {
            "event_type": "performance_issue",
            "operation": operation,
            "duration_ms": duration_ms,
            "threshold_ms": threshold_ms,
            "performance_ratio": duration_ms / threshold_ms,
            "timestamp": datetime.utcnow().isoformat()
        }
        
        if additional_metrics:
            log_data["metrics"] = additional_metrics
            
        self.logger.warning("Performance threshold exceeded", extra={"structured_data": log_data})
        
    def log_circuit_breaker_event(
        self,
        service_name: str,
        event_type: str,  # "opened", "closed", "half_open"
        failure_count: int,
        additional_info: Optional[Dict[str, Any]] = None
    ) -> None:
        """Log circuit breaker state changes."""
        log_data = {
            "event_type": "circuit_breaker_event",
            "service_name": service_name,
            "circuit_breaker_event": event_type,
            "failure_count": failure_count,
            "timestamp": datetime.utcnow().isoformat()
        }
        
        if additional_info:
            log_data["additional_info"] = additional_info
            
        log_level = logging.ERROR if event_type == "opened" else logging.INFO
        self.logger.log(log_level, f"Circuit breaker {event_type}", extra={"structured_data": log_data})
        
    def _get_log_level(self, error: Exception) -> int:
        """Determine appropriate log level for error."""
        if isinstance(error, MCPFinancialError):
            error_code = error.error_code
            if error_code in ["VALIDATION_ERROR", "AUTHENTICATION_ERROR"]:
                return logging.WARNING
            elif error_code in ["AUTHORIZATION_ERROR", "RATE_LIMIT_EXCEEDED"]:
                return logging.WARNING
            elif error_code in ["SERVICE_ERROR", "TIMEOUT_ERROR"]:
                return logging.ERROR
            elif error_code in ["CIRCUIT_BREAKER_OPEN", "INTERNAL_ERROR"]:
                return logging.CRITICAL
        
        # Default to error level
        return logging.ERROR


class StructuredFormatter(logging.Formatter):
    """Custom formatter for structured JSON logging."""
    
    def format(self, record: logging.LogRecord) -> str:
        """Format log record as structured JSON."""
        # Base log data
        log_data = {
            "timestamp": datetime.utcnow().isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "module": record.module,
            "function": record.funcName,
            "line": record.lineno
        }
        
        # Add structured data if present
        if hasattr(record, 'structured_data'):
            log_data.update(record.structured_data)
            
        # Add exception info if present
        if record.exc_info:
            log_data["exception"] = self.formatException(record.exc_info)
            
        return json.dumps(log_data, default=str, ensure_ascii=False)


class ErrorAggregator:
    """Aggregate and analyze error patterns."""
    
    def __init__(self, window_minutes: int = 60):
        self.window_minutes = window_minutes
        self.error_counts: Dict[str, int] = {}
        self.error_patterns: Dict[str, List[datetime]] = {}
        
    def record_error(self, error_type: str, operation: str) -> None:
        """Record an error occurrence."""
        key = f"{error_type}:{operation}"
        current_time = datetime.utcnow()
        
        # Initialize if not exists
        if key not in self.error_patterns:
            self.error_patterns[key] = []
            
        # Add current occurrence
        self.error_patterns[key].append(current_time)
        
        # Clean old occurrences
        self._clean_old_occurrences(key, current_time)
        
        # Update count
        self.error_counts[key] = len(self.error_patterns[key])
        
    def get_error_rate(self, error_type: str, operation: str) -> float:
        """Get error rate for specific error type and operation."""
        key = f"{error_type}:{operation}"
        return self.error_counts.get(key, 0) / self.window_minutes
        
    def get_top_errors(self, limit: int = 10) -> List[Dict[str, Any]]:
        """Get top errors by frequency."""
        sorted_errors = sorted(
            self.error_counts.items(),
            key=lambda x: x[1],
            reverse=True
        )
        
        result = []
        for key, count in sorted_errors[:limit]:
            error_type, operation = key.split(":", 1)
            result.append({
                "error_type": error_type,
                "operation": operation,
                "count": count,
                "rate_per_minute": count / self.window_minutes
            })
            
        return result
        
    def detect_error_spikes(self, threshold_multiplier: float = 3.0) -> List[Dict[str, Any]]:
        """Detect error spikes compared to historical average."""
        spikes = []
        
        for key, count in self.error_counts.items():
            # Simple spike detection - could be enhanced with more sophisticated algorithms
            average_rate = count / self.window_minutes
            if count > 10 and average_rate > threshold_multiplier:
                error_type, operation = key.split(":", 1)
                spikes.append({
                    "error_type": error_type,
                    "operation": operation,
                    "current_count": count,
                    "rate_per_minute": average_rate,
                    "severity": "high" if average_rate > threshold_multiplier * 2 else "medium"
                })
                
        return spikes
        
    def _clean_old_occurrences(self, key: str, current_time: datetime) -> None:
        """Remove occurrences outside the time window."""
        cutoff_time = current_time.replace(
            minute=current_time.minute - self.window_minutes
        )
        
        self.error_patterns[key] = [
            occurrence for occurrence in self.error_patterns[key]
            if occurrence > cutoff_time
        ]


@contextmanager
def error_logging_context(
    operation: str,
    user_id: Optional[str] = None,
    request_id: Optional[str] = None,
    logger: Optional[StructuredErrorLogger] = None
):
    """
    Context manager for automatic error logging.
    
    Usage:
        with error_logging_context("create_account", user_id="123") as ctx:
            # Operation code here
            pass
    """
    if logger is None:
        logger = StructuredErrorLogger()
        
    try:
        yield logger
    except Exception as e:
        logger.log_error(
            error=e,
            operation=operation,
            user_id=user_id,
            request_id=request_id
        )
        raise


# Global instances
error_logger = StructuredErrorLogger()
error_aggregator = ErrorAggregator()


def log_mcp_tool_error(
    error: Exception,
    tool_name: str,
    user_id: Optional[str] = None,
    request_id: Optional[str] = None,
    parameters: Optional[Dict[str, Any]] = None
) -> None:
    """
    Convenience function for logging MCP tool errors.
    
    Args:
        error: The exception that occurred
        tool_name: Name of the MCP tool
        user_id: User identifier
        request_id: Request identifier
        parameters: Tool parameters for context
    """
    additional_context = {}
    if parameters:
        # Sanitize parameters (remove sensitive data)
        sanitized_params = {
            k: v for k, v in parameters.items()
            if k not in ["auth_token", "password", "secret"]
        }
        additional_context["parameters"] = sanitized_params
        
    error_logger.log_error(
        error=error,
        operation=f"mcp_tool:{tool_name}",
        user_id=user_id,
        request_id=request_id,
        additional_context=additional_context
    )
    
    # Record for aggregation
    error_aggregator.record_error(type(error).__name__, tool_name)


def log_service_call_error(
    error: Exception,
    service_name: str,
    endpoint: str,
    method: str = "GET",
    status_code: Optional[int] = None,
    request_id: Optional[str] = None
) -> None:
    """
    Log service call errors with service-specific context.
    
    Args:
        error: The exception that occurred
        service_name: Name of the backend service
        endpoint: API endpoint
        method: HTTP method
        status_code: HTTP status code if available
        request_id: Request identifier
    """
    additional_context = {
        "service_name": service_name,
        "endpoint": endpoint,
        "method": method
    }
    
    if status_code:
        additional_context["status_code"] = status_code
        
    error_logger.log_error(
        error=error,
        operation=f"service_call:{service_name}",
        request_id=request_id,
        additional_context=additional_context
    )
    
    # Record for aggregation
    error_aggregator.record_error(type(error).__name__, f"{service_name}:{endpoint}")