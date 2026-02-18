"""
Alerting system integration for monitoring and notifications.
"""

import asyncio
import logging
import json
from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional, Callable
from enum import Enum
from dataclasses import dataclass, asdict
from abc import ABC, abstractmethod

import httpx

logger = logging.getLogger(__name__)


class AlertSeverity(Enum):
    """Alert severity levels."""
    CRITICAL = "critical"
    WARNING = "warning"
    INFO = "info"


class AlertType(Enum):
    """Types of alerts."""
    SERVICE_DOWN = "service_down"
    SERVICE_DEGRADED = "service_degraded"
    HIGH_ERROR_RATE = "high_error_rate"
    HIGH_RESPONSE_TIME = "high_response_time"
    AUTHENTICATION_FAILURE = "authentication_failure"
    CIRCUIT_BREAKER_OPEN = "circuit_breaker_open"
    RATE_LIMIT_EXCEEDED = "rate_limit_exceeded"
    SYSTEM_ERROR = "system_error"
    INFO = "info"


@dataclass
class Alert:
    """Alert data structure."""
    id: str
    type: AlertType
    severity: AlertSeverity
    title: str
    description: str
    timestamp: datetime
    source: str
    metadata: Dict[str, Any]
    resolved: bool = False
    resolved_at: Optional[datetime] = None


class AlertChannel(ABC):
    """Abstract base class for alert channels."""
    
    @abstractmethod
    async def send_alert(self, alert: Alert) -> bool:
        """Send an alert through this channel."""
        pass


class LogAlertChannel(AlertChannel):
    """Log-based alert channel."""
    
    def __init__(self, logger_name: str = "alerting"):
        self.logger = logging.getLogger(logger_name)
    
    async def send_alert(self, alert: Alert) -> bool:
        """Send alert to logs."""
        try:
            log_level = {
                AlertSeverity.CRITICAL: logging.CRITICAL,
                AlertSeverity.WARNING: logging.WARNING,
                AlertSeverity.INFO: logging.INFO
            }.get(alert.severity, logging.INFO)
            
            self.logger.log(
                log_level,
                f"ALERT: {alert.title}",
                extra={
                    "alert_id": alert.id,
                    "alert_type": alert.type.value,
                    "alert_severity": alert.severity.value,
                    "alert_description": alert.description,
                    "alert_source": alert.source,
                    "alert_metadata": alert.metadata,
                    "alert_timestamp": alert.timestamp.isoformat(),
                    "alert_resolved": alert.resolved
                }
            )
            return True
        except Exception as e:
            logger.error(f"Failed to send log alert: {e}")
            return False


class WebhookAlertChannel(AlertChannel):
    """Webhook-based alert channel."""
    
    def __init__(self, webhook_url: str, timeout: int = 10):
        self.webhook_url = webhook_url
        self.timeout = timeout
        self.client = httpx.AsyncClient(timeout=timeout)
    
    async def send_alert(self, alert: Alert) -> bool:
        """Send alert via webhook."""
        try:
            payload = {
                "alert": asdict(alert),
                "timestamp": alert.timestamp.isoformat(),
                "service": "mcp-financial-server"
            }
            
            # Convert datetime objects to strings
            if alert.resolved_at:
                payload["alert"]["resolved_at"] = alert.resolved_at.isoformat()
            payload["alert"]["timestamp"] = alert.timestamp.isoformat()
            payload["alert"]["type"] = alert.type.value
            payload["alert"]["severity"] = alert.severity.value
            
            response = await self.client.post(
                self.webhook_url,
                json=payload,
                headers={"Content-Type": "application/json"}
            )
            
            response.raise_for_status()
            return True
            
        except Exception as e:
            logger.error(f"Failed to send webhook alert: {e}")
            return False
    
    async def close(self):
        """Close the HTTP client."""
        await self.client.aclose()


class SlackAlertChannel(AlertChannel):
    """Slack-based alert channel."""
    
    def __init__(self, webhook_url: str, channel: str = "#alerts"):
        self.webhook_url = webhook_url
        self.channel = channel
        self.client = httpx.AsyncClient(timeout=10)
    
    async def send_alert(self, alert: Alert) -> bool:
        """Send alert to Slack."""
        try:
            # Color coding based on severity
            color_map = {
                AlertSeverity.CRITICAL: "#FF0000",  # Red
                AlertSeverity.WARNING: "#FFA500",   # Orange
                AlertSeverity.INFO: "#0000FF"       # Blue
            }
            
            # Create Slack message
            payload = {
                "channel": self.channel,
                "username": "MCP Financial Server",
                "icon_emoji": ":warning:",
                "attachments": [
                    {
                        "color": color_map.get(alert.severity, "#808080"),
                        "title": f"{alert.severity.value.upper()}: {alert.title}",
                        "text": alert.description,
                        "fields": [
                            {
                                "title": "Alert Type",
                                "value": alert.type.value,
                                "short": True
                            },
                            {
                                "title": "Source",
                                "value": alert.source,
                                "short": True
                            },
                            {
                                "title": "Timestamp",
                                "value": alert.timestamp.strftime("%Y-%m-%d %H:%M:%S UTC"),
                                "short": True
                            },
                            {
                                "title": "Alert ID",
                                "value": alert.id,
                                "short": True
                            }
                        ],
                        "footer": "MCP Financial Server Monitoring",
                        "ts": int(alert.timestamp.timestamp())
                    }
                ]
            }
            
            # Add metadata fields if present
            if alert.metadata:
                for key, value in alert.metadata.items():
                    payload["attachments"][0]["fields"].append({
                        "title": key.replace("_", " ").title(),
                        "value": str(value),
                        "short": True
                    })
            
            response = await self.client.post(
                self.webhook_url,
                json=payload
            )
            
            response.raise_for_status()
            return True
            
        except Exception as e:
            logger.error(f"Failed to send Slack alert: {e}")
            return False
    
    async def close(self):
        """Close the HTTP client."""
        await self.client.aclose()


class AlertManager:
    """Central alert management system."""
    
    def __init__(self):
        self.channels: List[AlertChannel] = []
        self.active_alerts: Dict[str, Alert] = {}
        self.alert_history: List[Alert] = []
        self.max_history = 1000
        
        # Alert suppression
        self.suppression_rules: Dict[str, timedelta] = {
            "service_down": timedelta(minutes=5),
            "high_error_rate": timedelta(minutes=2),
            "high_response_time": timedelta(minutes=3)
        }
        self.last_alert_times: Dict[str, datetime] = {}
        
        # Default log channel
        self.add_channel(LogAlertChannel())
    
    def add_channel(self, channel: AlertChannel):
        """Add an alert channel."""
        self.channels.append(channel)
        logger.info(f"Added alert channel: {type(channel).__name__}")
    
    def remove_channel(self, channel: AlertChannel):
        """Remove an alert channel."""
        if channel in self.channels:
            self.channels.remove(channel)
            logger.info(f"Removed alert channel: {type(channel).__name__}")
    
    async def send_alert(
        self,
        alert_type: AlertType,
        severity: AlertSeverity,
        title: str,
        description: str,
        source: str = "mcp-financial-server",
        metadata: Optional[Dict[str, Any]] = None
    ) -> str:
        """Send an alert through all configured channels."""
        
        # Check suppression
        suppression_key = f"{alert_type.value}:{source}"
        if self._is_suppressed(suppression_key, alert_type):
            logger.debug(f"Alert suppressed: {suppression_key}")
            return ""
        
        # Create alert
        alert_id = f"{alert_type.value}_{int(datetime.utcnow().timestamp())}"
        alert = Alert(
            id=alert_id,
            type=alert_type,
            severity=severity,
            title=title,
            description=description,
            timestamp=datetime.utcnow(),
            source=source,
            metadata=metadata or {}
        )
        
        # Store alert
        self.active_alerts[alert_id] = alert
        self.alert_history.append(alert)
        
        # Trim history
        if len(self.alert_history) > self.max_history:
            self.alert_history = self.alert_history[-self.max_history:]
        
        # Update suppression tracking
        self.last_alert_times[suppression_key] = alert.timestamp
        
        # Send through all channels
        success_count = 0
        for channel in self.channels:
            try:
                if await channel.send_alert(alert):
                    success_count += 1
            except Exception as e:
                logger.error(f"Alert channel {type(channel).__name__} failed: {e}")
        
        logger.info(
            f"Alert sent through {success_count}/{len(self.channels)} channels",
            extra={
                "alert_id": alert_id,
                "alert_type": alert_type.value,
                "severity": severity.value
            }
        )
        
        return alert_id
    
    async def resolve_alert(self, alert_id: str) -> bool:
        """Resolve an active alert."""
        if alert_id in self.active_alerts:
            alert = self.active_alerts[alert_id]
            alert.resolved = True
            alert.resolved_at = datetime.utcnow()
            
            # Send resolution notification
            await self.send_alert(
                AlertType.INFO,
                AlertSeverity.INFO,
                f"Alert Resolved: {alert.title}",
                f"Alert {alert_id} has been resolved",
                metadata={"resolved_alert_id": alert_id}
            )
            
            del self.active_alerts[alert_id]
            logger.info(f"Alert resolved: {alert_id}")
            return True
        
        return False
    
    def _is_suppressed(self, suppression_key: str, alert_type: AlertType) -> bool:
        """Check if alert should be suppressed."""
        if suppression_key not in self.last_alert_times:
            return False
        
        last_time = self.last_alert_times[suppression_key]
        suppression_period = self.suppression_rules.get(
            alert_type.value,
            timedelta(minutes=1)
        )
        
        return datetime.utcnow() - last_time < suppression_period
    
    def get_active_alerts(self) -> List[Alert]:
        """Get all active alerts."""
        return list(self.active_alerts.values())
    
    def get_alert_history(self, limit: int = 50) -> List[Alert]:
        """Get recent alert history."""
        return self.alert_history[-limit:]
    
    def get_alert_stats(self) -> Dict[str, Any]:
        """Get alerting statistics."""
        now = datetime.utcnow()
        last_24h = now - timedelta(hours=24)
        
        recent_alerts = [
            alert for alert in self.alert_history
            if alert.timestamp >= last_24h
        ]
        
        stats_by_type = {}
        stats_by_severity = {}
        
        for alert in recent_alerts:
            # Count by type
            alert_type = alert.type.value
            if alert_type not in stats_by_type:
                stats_by_type[alert_type] = 0
            stats_by_type[alert_type] += 1
            
            # Count by severity
            severity = alert.severity.value
            if severity not in stats_by_severity:
                stats_by_severity[severity] = 0
            stats_by_severity[severity] += 1
        
        return {
            "active_alerts": len(self.active_alerts),
            "total_alerts_24h": len(recent_alerts),
            "alerts_by_type_24h": stats_by_type,
            "alerts_by_severity_24h": stats_by_severity,
            "configured_channels": len(self.channels)
        }
    
    async def close(self):
        """Close all alert channels."""
        for channel in self.channels:
            if hasattr(channel, 'close'):
                try:
                    await channel.close()
                except Exception as e:
                    logger.error(f"Error closing alert channel: {e}")


# Global alert manager instance
alert_manager = AlertManager()


# Convenience functions
async def send_critical_alert(title: str, description: str, **kwargs):
    """Send a critical alert."""
    return await alert_manager.send_alert(
        AlertType.SYSTEM_ERROR,
        AlertSeverity.CRITICAL,
        title,
        description,
        **kwargs
    )


async def send_warning_alert(title: str, description: str, **kwargs):
    """Send a warning alert."""
    return await alert_manager.send_alert(
        AlertType.SYSTEM_ERROR,
        AlertSeverity.WARNING,
        title,
        description,
        **kwargs
    )


async def send_info_alert(title: str, description: str, **kwargs):
    """Send an info alert."""
    return await alert_manager.send_alert(
        AlertType.SYSTEM_ERROR,
        AlertSeverity.INFO,
        title,
        description,
        **kwargs
    )


class MonitoringAlerts:
    """Monitoring-specific alert helpers."""
    
    @staticmethod
    async def service_down_alert(service_name: str, error: str):
        """Send service down alert."""
        return await alert_manager.send_alert(
            AlertType.SERVICE_DOWN,
            AlertSeverity.CRITICAL,
            f"Service Down: {service_name}",
            f"Service {service_name} is not responding: {error}",
            metadata={"service": service_name, "error": error}
        )
    
    @staticmethod
    async def high_response_time_alert(service_name: str, response_time: float):
        """Send high response time alert."""
        return await alert_manager.send_alert(
            AlertType.HIGH_RESPONSE_TIME,
            AlertSeverity.WARNING,
            f"High Response Time: {service_name}",
            f"Service {service_name} response time is {response_time:.2f}ms",
            metadata={"service": service_name, "response_time_ms": response_time}
        )
    
    @staticmethod
    async def circuit_breaker_alert(service_name: str, state: str):
        """Send circuit breaker alert."""
        severity = AlertSeverity.CRITICAL if state == "OPEN" else AlertSeverity.WARNING
        return await alert_manager.send_alert(
            AlertType.CIRCUIT_BREAKER_OPEN,
            severity,
            f"Circuit Breaker {state}: {service_name}",
            f"Circuit breaker for {service_name} is now {state}",
            metadata={"service": service_name, "circuit_breaker_state": state}
        )
    
    @staticmethod
    async def authentication_failure_alert(reason: str, count: int):
        """Send authentication failure alert."""
        return await alert_manager.send_alert(
            AlertType.AUTHENTICATION_FAILURE,
            AlertSeverity.WARNING,
            "Authentication Failures Detected",
            f"Multiple authentication failures detected: {reason} (count: {count})",
            metadata={"failure_reason": reason, "failure_count": count}
        )