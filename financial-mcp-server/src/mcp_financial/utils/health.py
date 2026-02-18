"""
Health check and service status monitoring utilities.
"""

import asyncio
import logging
import time
from datetime import datetime, timedelta
from typing import Dict, Any, Optional, List
from enum import Enum
from dataclasses import dataclass

import httpx

logger = logging.getLogger(__name__)


class ServiceStatus(Enum):
    """Service health status enumeration."""
    HEALTHY = "healthy"
    UNHEALTHY = "unhealthy"
    DEGRADED = "degraded"
    UNKNOWN = "unknown"


@dataclass
class HealthCheckResult:
    """Health check result data class."""
    service: str
    status: ServiceStatus
    response_time_ms: Optional[float]
    error: Optional[str]
    timestamp: datetime
    details: Optional[Dict[str, Any]] = None


class HealthChecker:
    """Health checker for monitoring service dependencies."""
    
    def __init__(
        self,
        account_service_url: str,
        transaction_service_url: str,
        timeout: int = 5000
    ):
        self.account_service_url = account_service_url
        self.transaction_service_url = transaction_service_url
        self.timeout = timeout / 1000  # Convert to seconds
        self.client = httpx.AsyncClient(timeout=self.timeout)
        
        # Health check cache
        self._health_cache: Dict[str, HealthCheckResult] = {}
        self._cache_ttl = 30  # seconds
        
    async def check_service_health(self, service_name: str, url: str) -> HealthCheckResult:
        """Check health of a specific service."""
        start_time = time.time()
        
        try:
            # Try health endpoint first, fallback to root if not available
            health_endpoints = ["/actuator/health", "/health", "/"]
            
            for endpoint in health_endpoints:
                try:
                    response = await self.client.get(f"{url}{endpoint}")
                    response_time = (time.time() - start_time) * 1000
                    
                    if response.status_code == 200:
                        return HealthCheckResult(
                            service=service_name,
                            status=ServiceStatus.HEALTHY,
                            response_time_ms=response_time,
                            error=None,
                            timestamp=datetime.utcnow(),
                            details={"endpoint": endpoint, "status_code": response.status_code}
                        )
                    elif response.status_code in [503, 500]:
                        return HealthCheckResult(
                            service=service_name,
                            status=ServiceStatus.UNHEALTHY,
                            response_time_ms=response_time,
                            error=f"Service returned {response.status_code}",
                            timestamp=datetime.utcnow(),
                            details={"endpoint": endpoint, "status_code": response.status_code}
                        )
                except httpx.RequestError:
                    continue  # Try next endpoint
                    
            # If all endpoints failed
            response_time = (time.time() - start_time) * 1000
            return HealthCheckResult(
                service=service_name,
                status=ServiceStatus.UNHEALTHY,
                response_time_ms=response_time,
                error="All health endpoints failed",
                timestamp=datetime.utcnow()
            )
            
        except Exception as e:
            response_time = (time.time() - start_time) * 1000
            return HealthCheckResult(
                service=service_name,
                status=ServiceStatus.UNKNOWN,
                response_time_ms=response_time,
                error=str(e),
                timestamp=datetime.utcnow()
            )
    
    async def check_account_service(self) -> HealthCheckResult:
        """Check Account Service health."""
        return await self.check_service_health("account-service", self.account_service_url)
    
    async def check_transaction_service(self) -> HealthCheckResult:
        """Check Transaction Service health."""
        return await self.check_service_health("transaction-service", self.transaction_service_url)
    
    async def check_all_services(self, use_cache: bool = True) -> Dict[str, HealthCheckResult]:
        """Check health of all services."""
        if use_cache:
            # Check if we have recent cached results
            now = datetime.utcnow()
            cached_results = {}
            
            for service, result in self._health_cache.items():
                if (now - result.timestamp).total_seconds() < self._cache_ttl:
                    cached_results[service] = result
            
            if len(cached_results) == 2:  # Both services cached
                return cached_results
        
        # Perform health checks concurrently
        tasks = [
            self.check_account_service(),
            self.check_transaction_service()
        ]
        
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        health_results = {}
        for result in results:
            if isinstance(result, HealthCheckResult):
                health_results[result.service] = result
                self._health_cache[result.service] = result
            else:
                logger.error(f"Health check failed with exception: {result}")
        
        return health_results
    
    async def get_overall_health(self) -> Dict[str, Any]:
        """Get overall system health status."""
        service_results = await self.check_all_services()
        
        # Determine overall status
        statuses = [result.status for result in service_results.values()]
        
        if all(status == ServiceStatus.HEALTHY for status in statuses):
            overall_status = ServiceStatus.HEALTHY
        elif any(status == ServiceStatus.UNHEALTHY for status in statuses):
            overall_status = ServiceStatus.UNHEALTHY
        elif any(status == ServiceStatus.DEGRADED for status in statuses):
            overall_status = ServiceStatus.DEGRADED
        else:
            overall_status = ServiceStatus.UNKNOWN
        
        # Calculate average response time
        response_times = [
            result.response_time_ms 
            for result in service_results.values() 
            if result.response_time_ms is not None
        ]
        avg_response_time = sum(response_times) / len(response_times) if response_times else None
        
        return {
            "status": overall_status.value,
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "services": {
                service: {
                    "status": result.status.value,
                    "response_time_ms": result.response_time_ms,
                    "error": result.error,
                    "timestamp": result.timestamp.isoformat() + "Z",
                    "details": result.details
                }
                for service, result in service_results.items()
            },
            "metrics": {
                "total_services": len(service_results),
                "healthy_services": sum(1 for r in service_results.values() if r.status == ServiceStatus.HEALTHY),
                "unhealthy_services": sum(1 for r in service_results.values() if r.status == ServiceStatus.UNHEALTHY),
                "average_response_time_ms": avg_response_time
            }
        }
    
    async def close(self):
        """Close the HTTP client."""
        await self.client.aclose()


class SystemHealthMonitor:
    """System-wide health monitoring."""
    
    def __init__(self, health_checker: HealthChecker):
        self.health_checker = health_checker
        self._monitoring_task: Optional[asyncio.Task] = None
        self._monitoring_interval = 60  # seconds
        self._health_history: List[Dict[str, Any]] = []
        self._max_history = 100
        
    async def start_monitoring(self):
        """Start continuous health monitoring."""
        if self._monitoring_task and not self._monitoring_task.done():
            logger.warning("Health monitoring already running")
            return
            
        logger.info("Starting health monitoring")
        self._monitoring_task = asyncio.create_task(self._monitoring_loop())
        
    async def stop_monitoring(self):
        """Stop continuous health monitoring."""
        if self._monitoring_task:
            self._monitoring_task.cancel()
            try:
                await self._monitoring_task
            except asyncio.CancelledError:
                pass
            logger.info("Health monitoring stopped")
    
    async def _monitoring_loop(self):
        """Continuous monitoring loop."""
        while True:
            try:
                health_status = await self.health_checker.get_overall_health()
                
                # Store in history
                self._health_history.append(health_status)
                if len(self._health_history) > self._max_history:
                    self._health_history.pop(0)
                
                # Log health status
                logger.info(
                    "Health check completed",
                    extra={
                        "overall_status": health_status["status"],
                        "healthy_services": health_status["metrics"]["healthy_services"],
                        "total_services": health_status["metrics"]["total_services"],
                        "avg_response_time": health_status["metrics"]["average_response_time_ms"]
                    }
                )
                
                # Check for alerts
                await self._check_alerts(health_status)
                
            except Exception as e:
                logger.error(f"Health monitoring error: {e}")
            
            await asyncio.sleep(self._monitoring_interval)
    
    async def _check_alerts(self, health_status: Dict[str, Any]):
        """Check for alert conditions."""
        # Alert on unhealthy services
        for service_name, service_info in health_status["services"].items():
            if service_info["status"] == ServiceStatus.UNHEALTHY.value:
                logger.error(
                    "Service unhealthy alert",
                    extra={
                        "service": service_name,
                        "error": service_info["error"],
                        "alert_type": "service_unhealthy"
                    }
                )
        
        # Alert on high response times
        avg_response_time = health_status["metrics"]["average_response_time_ms"]
        if avg_response_time and avg_response_time > 5000:  # 5 seconds
            logger.warning(
                "High response time alert",
                extra={
                    "avg_response_time_ms": avg_response_time,
                    "alert_type": "high_response_time"
                }
            )
    
    def get_health_history(self, limit: int = 10) -> List[Dict[str, Any]]:
        """Get recent health check history."""
        return self._health_history[-limit:]
    
    def get_health_summary(self) -> Dict[str, Any]:
        """Get health monitoring summary."""
        if not self._health_history:
            return {"status": "no_data", "message": "No health data available"}
        
        latest = self._health_history[-1]
        
        # Calculate uptime statistics
        recent_checks = self._health_history[-20:]  # Last 20 checks
        uptime_stats = {}
        
        for service_name in latest["services"].keys():
            healthy_count = sum(
                1 for check in recent_checks 
                if check["services"].get(service_name, {}).get("status") == "healthy"
            )
            uptime_stats[service_name] = {
                "uptime_percentage": (healthy_count / len(recent_checks)) * 100,
                "total_checks": len(recent_checks),
                "healthy_checks": healthy_count
            }
        
        return {
            "current_status": latest,
            "uptime_stats": uptime_stats,
            "monitoring_active": self._monitoring_task and not self._monitoring_task.done(),
            "history_count": len(self._health_history)
        }