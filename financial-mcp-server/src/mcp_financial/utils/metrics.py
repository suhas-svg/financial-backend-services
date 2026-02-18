"""
Prometheus metrics collection for MCP operations.
"""

import time
import logging
from typing import Dict, Any, Optional
from functools import wraps
from prometheus_client import Counter, Histogram, Gauge, CollectorRegistry, start_http_server

logger = logging.getLogger(__name__)

# Global metrics registry
REGISTRY = CollectorRegistry()

# MCP-specific metrics
mcp_requests_total = Counter(
    'mcp_requests_total',
    'Total MCP requests',
    ['tool', 'status'],
    registry=REGISTRY
)

mcp_request_duration = Histogram(
    'mcp_request_duration_seconds',
    'MCP request duration in seconds',
    ['tool'],
    registry=REGISTRY
)

mcp_active_connections = Gauge(
    'mcp_active_connections',
    'Active MCP connections',
    registry=REGISTRY
)

# Service integration metrics
service_requests_total = Counter(
    'service_requests_total',
    'Backend service requests',
    ['service', 'endpoint', 'status'],
    registry=REGISTRY
)

service_request_duration = Histogram(
    'service_request_duration_seconds',
    'Service request duration in seconds',
    ['service', 'endpoint'],
    registry=REGISTRY
)

circuit_breaker_state = Gauge(
    'circuit_breaker_state',
    'Circuit breaker state (0=closed, 1=open, 2=half-open)',
    ['service'],
    registry=REGISTRY
)

# Authentication metrics
auth_requests_total = Counter(
    'auth_requests_total',
    'Authentication requests',
    ['status'],
    registry=REGISTRY
)

auth_failures_total = Counter(
    'auth_failures_total',
    'Authentication failures',
    ['reason'],
    registry=REGISTRY
)

# Error metrics
errors_total = Counter(
    'errors_total',
    'Total errors',
    ['error_type', 'component'],
    registry=REGISTRY
)

error_counter = Counter(
    'mcp_errors_total',
    'Total MCP errors by operation and type',
    ['operation', 'error_type', 'category', 'severity'],
    registry=REGISTRY
)

error_duration = Histogram(
    'mcp_error_handling_duration_seconds',
    'Time spent handling errors',
    ['operation', 'error_type'],
    registry=REGISTRY
)

validation_errors_total = Counter(
    'validation_errors_total',
    'Total validation errors',
    ['field', 'error_type'],
    registry=REGISTRY
)

circuit_breaker_events = Counter(
    'circuit_breaker_events_total',
    'Circuit breaker state change events',
    ['service', 'event_type'],
    registry=REGISTRY
)

rate_limit_violations = Counter(
    'rate_limit_violations_total',
    'Rate limit violations',
    ['user_id', 'operation'],
    registry=REGISTRY
)

security_events = Counter(
    'security_events_total',
    'Security-related events',
    ['event_type', 'severity'],
    registry=REGISTRY
)

# Account operation metrics
account_operations_counter = Counter(
    'account_operations_total',
    'Total account operations',
    ['operation', 'status'],
    registry=REGISTRY
)

account_operation_duration = Histogram(
    'account_operation_duration_seconds',
    'Account operation duration in seconds',
    ['operation'],
    registry=REGISTRY
)

# Transaction operation metrics
transaction_operations_counter = Counter(
    'transaction_operations_total',
    'Total transaction operations',
    ['operation', 'status'],
    registry=REGISTRY
)

transaction_operation_duration = Histogram(
    'transaction_operation_duration_seconds',
    'Transaction operation duration in seconds',
    ['operation'],
    registry=REGISTRY
)

transaction_amounts_histogram = Histogram(
    'transaction_amounts',
    'Transaction amounts distribution',
    ['transaction_type'],
    registry=REGISTRY
)

# Query operation metrics
query_operations_counter = Counter(
    'query_operations_total',
    'Total query operations',
    ['query_type', 'status'],
    registry=REGISTRY
)

query_operation_duration = Histogram(
    'query_operation_duration_seconds',
    'Query operation duration in seconds',
    ['query_type'],
    registry=REGISTRY
)

query_results_histogram = Histogram(
    'query_results_count',
    'Number of results returned by queries',
    ['query_type'],
    registry=REGISTRY
)


class MetricsCollector:
    """Metrics collection utility."""
    
    @staticmethod
    def record_mcp_request(tool_name: str, status: str, duration: float):
        """Record MCP request metrics."""
        mcp_requests_total.labels(tool=tool_name, status=status).inc()
        mcp_request_duration.labels(tool=tool_name).observe(duration)
        
    @staticmethod
    def record_service_request(
        service: str, 
        endpoint: str, 
        status: str, 
        duration: float
    ):
        """Record service request metrics."""
        service_requests_total.labels(
            service=service, 
            endpoint=endpoint, 
            status=status
        ).inc()
        service_request_duration.labels(
            service=service, 
            endpoint=endpoint
        ).observe(duration)
        
    @staticmethod
    def set_circuit_breaker_state(service: str, state: str):
        """Set circuit breaker state metric."""
        state_value = {"CLOSED": 0, "OPEN": 1, "HALF_OPEN": 2}.get(state, 0)
        circuit_breaker_state.labels(service=service).set(state_value)
        
    @staticmethod
    def record_auth_request(status: str):
        """Record authentication request."""
        auth_requests_total.labels(status=status).inc()
        
    @staticmethod
    def record_auth_failure(reason: str):
        """Record authentication failure."""
        auth_failures_total.labels(reason=reason).inc()
        
    @staticmethod
    def record_error(error_type: str, component: str):
        """Record error occurrence."""
        errors_total.labels(error_type=error_type, component=component).inc()
        
    @staticmethod
    def increment_active_connections():
        """Increment active connections counter."""
        mcp_active_connections.inc()
        
    @staticmethod
    def decrement_active_connections():
        """Decrement active connections counter."""
        mcp_active_connections.dec()
        
    @staticmethod
    def record_validation_error(field: str, error_type: str):
        """Record validation error."""
        validation_errors_total.labels(field=field, error_type=error_type).inc()
        
    @staticmethod
    def record_circuit_breaker_event(service: str, event_type: str):
        """Record circuit breaker event."""
        circuit_breaker_events.labels(service=service, event_type=event_type).inc()
        
    @staticmethod
    def record_rate_limit_violation(user_id: str, operation: str):
        """Record rate limit violation."""
        rate_limit_violations.labels(user_id=user_id, operation=operation).inc()
        
    @staticmethod
    def record_security_event(event_type: str, severity: str):
        """Record security event."""
        security_events.labels(event_type=event_type, severity=severity).inc()


def track_mcp_request(tool_name: str):
    """Decorator to track MCP request metrics."""
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            start_time = time.time()
            status = "success"
            
            try:
                result = await func(*args, **kwargs)
                return result
            except Exception as e:
                status = "error"
                MetricsCollector.record_error(
                    error_type=type(e).__name__,
                    component="mcp_tool"
                )
                raise
            finally:
                duration = time.time() - start_time
                MetricsCollector.record_mcp_request(tool_name, status, duration)
                
        return wrapper
    return decorator


def track_service_request(service: str, endpoint: str):
    """Decorator to track service request metrics."""
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            start_time = time.time()
            status = "success"
            
            try:
                result = await func(*args, **kwargs)
                return result
            except Exception as e:
                status = "error"
                MetricsCollector.record_error(
                    error_type=type(e).__name__,
                    component="service_client"
                )
                raise
            finally:
                duration = time.time() - start_time
                MetricsCollector.record_service_request(service, endpoint, status, duration)
                
        return wrapper
    return decorator


def setup_metrics(port: int = 9090, enabled: bool = True) -> Optional[object]:
    """
    Setup Prometheus metrics server.
    
    Args:
        port: Port to serve metrics on
        enabled: Whether metrics collection is enabled
        
    Returns:
        HTTP server instance if started, None otherwise
    """
    if not enabled:
        logger.info("Metrics collection disabled")
        return None
        
    try:
        server = start_http_server(port, registry=REGISTRY)
        logger.info(f"Metrics server started on port {port}")
        return server
    except Exception as e:
        logger.error(f"Failed to start metrics server: {e}")
        return None


class QueryToolsMetrics:
    """Metrics collection for query tools."""
    
    def __init__(self):
        self.transaction_history_requests = Counter(
            'query_transaction_history_requests_total',
            'Transaction history query requests',
            registry=REGISTRY
        )
        
        self.transaction_search_requests = Counter(
            'query_transaction_search_requests_total',
            'Transaction search query requests',
            registry=REGISTRY
        )
        
        self.account_analytics_requests = Counter(
            'query_account_analytics_requests_total',
            'Account analytics query requests',
            registry=REGISTRY
        )
        
        self.transaction_limits_requests = Counter(
            'query_transaction_limits_requests_total',
            'Transaction limits query requests',
            registry=REGISTRY
        )
        
        self.query_errors = Counter(
            'query_errors_total',
            'Query operation errors',
            registry=REGISTRY
        )


# Global query tools metrics instance
query_tools_metrics = QueryToolsMetrics()


def get_metrics_summary() -> Dict[str, Any]:
    """Get current metrics summary."""
    try:
        # Safely get metric values with fallbacks
        def safe_get_counter_value(counter):
            try:
                # Try different ways to get counter value
                if hasattr(counter, '_value'):
                    if hasattr(counter._value, 'sum'):
                        return counter._value.sum()
                    elif hasattr(counter._value, '_value'):
                        return counter._value._value
                    else:
                        return float(counter._value)
                return 0.0
            except:
                return 0.0
        
        def safe_get_gauge_value(gauge):
            try:
                if hasattr(gauge, '_value'):
                    if hasattr(gauge._value, '_value'):
                        return gauge._value._value
                    else:
                        return float(gauge._value)
                return 0.0
            except:
                return 0.0
        
        return {
            "total_requests": safe_get_counter_value(mcp_requests_total),
            "active_connections": safe_get_gauge_value(mcp_active_connections),
            "total_errors": safe_get_counter_value(errors_total),
            "auth_requests": safe_get_counter_value(auth_requests_total),
            "auth_failures": safe_get_counter_value(auth_failures_total),
            "query_operations": {
                "transaction_history": safe_get_counter_value(query_tools_metrics.transaction_history_requests),
                "transaction_search": safe_get_counter_value(query_tools_metrics.transaction_search_requests),
                "account_analytics": safe_get_counter_value(query_tools_metrics.account_analytics_requests),
                "transaction_limits": safe_get_counter_value(query_tools_metrics.transaction_limits_requests),
                "query_errors": safe_get_counter_value(query_tools_metrics.query_errors)
            }
        }
    except Exception as e:
        logger.error(f"Error getting metrics summary: {e}")
        return {
            "total_requests": 0,
            "active_connections": 0,
            "total_errors": 0,
            "auth_requests": 0,
            "auth_failures": 0,
            "query_operations": {
                "transaction_history": 0,
                "transaction_search": 0,
                "account_analytics": 0,
                "transaction_limits": 0,
                "query_errors": 0
            }
        }