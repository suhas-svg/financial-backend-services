"""
Base HTTP client with common functionality including retry logic and circuit breaker.
"""

import asyncio
import logging
from typing import Dict, Any, Optional, Union
from datetime import datetime, timedelta
import httpx
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

logger = logging.getLogger(__name__)


class CircuitBreakerError(Exception):
    """Circuit breaker is open."""
    pass


class ServiceUnavailableError(Exception):
    """Service is unavailable."""
    pass


class CircuitBreaker:
    """Enhanced circuit breaker implementation with better error handling."""
    
    def __init__(self, failure_threshold: int = 5, recovery_timeout: int = 30, half_open_max_calls: int = 3):
        self.failure_threshold = failure_threshold
        self.recovery_timeout = recovery_timeout
        self.half_open_max_calls = half_open_max_calls
        self.failure_count = 0
        self.last_failure_time: Optional[datetime] = None
        self.state = "CLOSED"  # CLOSED, OPEN, HALF_OPEN
        self.half_open_calls = 0
        self.consecutive_successes = 0
        
    def call(self, func):
        """Decorator for circuit breaker functionality."""
        def wrapper(*args, **kwargs):
            if self.state == "OPEN":
                if self._should_attempt_reset():
                    self.state = "HALF_OPEN"
                    self.half_open_calls = 0
                    logger.info("Circuit breaker transitioning to HALF_OPEN")
                else:
                    raise CircuitBreakerError("Circuit breaker is OPEN")
                    
            if self.state == "HALF_OPEN":
                if self.half_open_calls >= self.half_open_max_calls:
                    raise CircuitBreakerError("Circuit breaker HALF_OPEN call limit exceeded")
                self.half_open_calls += 1
                    
            try:
                result = func(*args, **kwargs)
                self._on_success()
                return result
            except Exception as e:
                self._on_failure(e)
                raise
                
        return wrapper
        
    def _should_attempt_reset(self) -> bool:
        """Check if circuit breaker should attempt reset."""
        if self.last_failure_time is None:
            return True
        return datetime.utcnow() - self.last_failure_time > timedelta(seconds=self.recovery_timeout)
        
    def _on_success(self):
        """Handle successful call."""
        self.consecutive_successes += 1
        
        if self.state == "HALF_OPEN":
            if self.consecutive_successes >= 3:  # Require multiple successes to close
                self.state = "CLOSED"
                self.failure_count = 0
                self.consecutive_successes = 0
                logger.info("Circuit breaker reset to CLOSED after successful calls")
        elif self.state == "CLOSED":
            # Reset failure count on successful calls
            if self.failure_count > 0:
                self.failure_count = max(0, self.failure_count - 1)
            
    def _on_failure(self, exception: Exception):
        """Handle failed call with exception context."""
        self.failure_count += 1
        self.last_failure_time = datetime.utcnow()
        self.consecutive_successes = 0
        
        # Log failure details
        logger.warning(f"Circuit breaker failure #{self.failure_count}: {str(exception)}")
        
        if self.state == "HALF_OPEN":
            # Immediately open on any failure in half-open state
            self.state = "OPEN"
            logger.warning("Circuit breaker opened from HALF_OPEN due to failure")
        elif self.failure_count >= self.failure_threshold:
            self.state = "OPEN"
            logger.error(f"Circuit breaker opened after {self.failure_count} failures")
            
    def get_state(self) -> Dict[str, Any]:
        """Get current circuit breaker state information."""
        return {
            "state": self.state,
            "failure_count": self.failure_count,
            "consecutive_successes": self.consecutive_successes,
            "last_failure_time": self.last_failure_time.isoformat() if self.last_failure_time else None,
            "half_open_calls": self.half_open_calls if self.state == "HALF_OPEN" else 0
        }


class BaseHTTPClient:
    """Base HTTP client with retry logic and circuit breaker."""
    
    def __init__(
        self, 
        base_url: str, 
        timeout: int = 5000,
        max_retries: int = 3,
        retry_delay: float = 1.0,
        circuit_breaker_failure_threshold: int = 5,
        circuit_breaker_recovery_timeout: int = 30
    ):
        self.base_url = base_url.rstrip('/')
        self.timeout = timeout / 1000  # Convert to seconds
        self.max_retries = max_retries
        self.retry_delay = retry_delay
        
        # Initialize HTTP client
        self.client = httpx.AsyncClient(
            timeout=httpx.Timeout(self.timeout),
            limits=httpx.Limits(max_keepalive_connections=20, max_connections=100)
        )
        
        # Initialize circuit breaker
        self.circuit_breaker = CircuitBreaker(
            failure_threshold=circuit_breaker_failure_threshold,
            recovery_timeout=circuit_breaker_recovery_timeout
        )
        
    async def __aenter__(self):
        return self
        
    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.close()
        
    async def close(self):
        """Close the HTTP client."""
        await self.client.aclose()
        
    def _get_headers(self, auth_token: Optional[str] = None) -> Dict[str, str]:
        """Get default headers for requests."""
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "MCP-Financial-Server/1.0"
        }
        
        if auth_token:
            if not auth_token.startswith('Bearer '):
                auth_token = f"Bearer {auth_token}"
            headers["Authorization"] = auth_token
            
        return headers
        
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=1, max=10),
        retry=retry_if_exception_type((httpx.TimeoutException, httpx.ConnectError))
    )
    async def _make_request(
        self,
        method: str,
        endpoint: str,
        data: Optional[Dict[str, Any]] = None,
        params: Optional[Dict[str, Any]] = None,
        auth_token: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Make HTTP request with retry logic and circuit breaker.
        
        Args:
            method: HTTP method (GET, POST, PUT, DELETE)
            endpoint: API endpoint (without base URL)
            data: Request body data
            params: Query parameters
            auth_token: JWT authentication token
            
        Returns:
            Response data as dictionary
            
        Raises:
            ServiceUnavailableError: If service is unavailable
            CircuitBreakerError: If circuit breaker is open
            httpx.HTTPStatusError: For HTTP errors
        """
        url = f"{self.base_url}{endpoint}"
        headers = self._get_headers(auth_token)
        
        logger.debug(f"Making {method} request to {url}")
        
        @self.circuit_breaker.call
        async def make_request():
            try:
                response = await self.client.request(
                    method=method,
                    url=url,
                    json=data,
                    params=params,
                    headers=headers
                )
                
                # Log response details
                logger.debug(f"Response: {response.status_code} from {url}")
                
                # Handle different response status codes with specific error types
                if response.status_code == 400:
                    error_detail = self._extract_error_details(response)
                    from ..exceptions.base import ValidationError
                    raise ValidationError(
                        message=f"Bad request to {url}: {error_detail}",
                        details={"status_code": 400, "url": url, "response": error_detail}
                    )
                elif response.status_code == 401:
                    from ..exceptions.base import AuthenticationError
                    raise AuthenticationError(
                        message=f"Authentication failed for {url}",
                        details={"status_code": 401, "url": url}
                    )
                elif response.status_code == 403:
                    from ..exceptions.base import AuthorizationError
                    raise AuthorizationError(
                        message=f"Access denied to {url}",
                        details={"status_code": 403, "url": url}
                    )
                elif response.status_code == 404:
                    from ..exceptions.base import ValidationError
                    raise ValidationError(
                        message=f"Resource not found: {url}",
                        details={"status_code": 404, "url": url}
                    )
                elif response.status_code == 409:
                    error_detail = self._extract_error_details(response)
                    from ..exceptions.base import BusinessRuleError
                    raise BusinessRuleError(
                        message=f"Conflict: {error_detail}",
                        details={"status_code": 409, "url": url, "response": error_detail}
                    )
                elif response.status_code == 429:
                    from ..exceptions.base import RateLimitError
                    raise RateLimitError(
                        message=f"Rate limit exceeded for {url}",
                        details={"status_code": 429, "url": url}
                    )
                elif response.status_code >= 500:
                    error_detail = self._extract_error_details(response)
                    from ..exceptions.base import ServiceError
                    raise ServiceError(
                        message=f"Server error from {url}: {error_detail}",
                        service_name=self._extract_service_name(url),
                        status_code=response.status_code,
                        details={"url": url, "response": error_detail}
                    )
                    
                response.raise_for_status()
                
                # Return JSON response or empty dict for no content
                if response.status_code == 204:
                    return {}
                    
                try:
                    return response.json()
                except ValueError:
                    # Handle non-JSON responses
                    return {"response": response.text}
                
            except httpx.TimeoutException as e:
                logger.warning(f"Request timeout for {url}: {str(e)}")
                from ..exceptions.base import TimeoutError
                raise TimeoutError(
                    message=f"Request timeout: {url}",
                    timeout_seconds=self.timeout,
                    operation=f"{method} {endpoint}",
                    details={"url": url, "timeout": self.timeout}
                )
            except httpx.ConnectError as e:
                logger.warning(f"Connection error for {url}: {str(e)}")
                from ..exceptions.base import ServiceError
                raise ServiceError(
                    message=f"Connection error: {url}",
                    service_name=self._extract_service_name(url),
                    details={"url": url, "error": str(e)}
                )
            except httpx.HTTPStatusError as e:
                # This should be handled above, but just in case
                logger.warning(f"HTTP error {e.response.status_code} for {url}: {str(e)}")
                from ..exceptions.base import ServiceError
                raise ServiceError(
                    message=f"HTTP error {e.response.status_code}: {url}",
                    service_name=self._extract_service_name(url),
                    status_code=e.response.status_code,
                    details={"url": url, "error": str(e)}
                )
            except Exception as e:
                logger.error(f"Unexpected error for {url}: {str(e)}", exc_info=True)
                from ..exceptions.base import ServiceError
                raise ServiceError(
                    message=f"Unexpected error: {url}",
                    service_name=self._extract_service_name(url),
                    details={"url": url, "error": str(e), "type": type(e).__name__}
                )
                
        return await make_request()
        
    async def get(
        self, 
        endpoint: str, 
        params: Optional[Dict[str, Any]] = None,
        auth_token: Optional[str] = None
    ) -> Dict[str, Any]:
        """Make GET request."""
        return await self._make_request("GET", endpoint, params=params, auth_token=auth_token)
        
    async def post(
        self, 
        endpoint: str, 
        data: Optional[Dict[str, Any]] = None,
        auth_token: Optional[str] = None
    ) -> Dict[str, Any]:
        """Make POST request."""
        return await self._make_request("POST", endpoint, data=data, auth_token=auth_token)
        
    async def put(
        self, 
        endpoint: str, 
        data: Optional[Dict[str, Any]] = None,
        auth_token: Optional[str] = None
    ) -> Dict[str, Any]:
        """Make PUT request."""
        return await self._make_request("PUT", endpoint, data=data, auth_token=auth_token)
        
    async def delete(
        self, 
        endpoint: str,
        auth_token: Optional[str] = None
    ) -> Dict[str, Any]:
        """Make DELETE request."""
        return await self._make_request("DELETE", endpoint, auth_token=auth_token)
        
    def _extract_error_details(self, response: httpx.Response) -> str:
        """Extract error details from response."""
        try:
            error_data = response.json()
            if isinstance(error_data, dict):
                # Try common error message fields
                for field in ['message', 'error', 'detail', 'error_description']:
                    if field in error_data:
                        return str(error_data[field])
                # Return the whole response if no standard field found
                return str(error_data)
            return str(error_data)
        except (ValueError, TypeError):
            # Fallback to response text
            return response.text or f"HTTP {response.status_code}"
            
    def _extract_service_name(self, url: str) -> str:
        """Extract service name from URL."""
        try:
            # Extract from base URL
            if "account" in self.base_url.lower():
                return "account-service"
            elif "transaction" in self.base_url.lower():
                return "transaction-service"
            else:
                # Extract from hostname
                from urllib.parse import urlparse
                parsed = urlparse(url)
                return parsed.hostname or "unknown-service"
        except Exception:
            return "unknown-service"
            
    async def health_check(self) -> Dict[str, Any]:
        """Check if the service is healthy with detailed status."""
        try:
            response = await self.get("/actuator/health")
            return {
                "healthy": True,
                "status": response.get("status", "UP"),
                "details": response,
                "circuit_breaker": self.circuit_breaker.get_state()
            }
        except Exception as e:
            logger.warning(f"Health check failed for {self.base_url}: {str(e)}")
            return {
                "healthy": False,
                "error": str(e),
                "circuit_breaker": self.circuit_breaker.get_state()
            }
            
    def get_metrics(self) -> Dict[str, Any]:
        """Get client metrics and status."""
        return {
            "base_url": self.base_url,
            "timeout": self.timeout,
            "max_retries": self.max_retries,
            "circuit_breaker": self.circuit_breaker.get_state()
        }