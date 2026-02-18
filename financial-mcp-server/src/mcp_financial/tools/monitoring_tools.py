"""
Monitoring and observability MCP tools.
"""

import logging
from typing import List, Dict, Any
from mcp.types import Tool, TextContent
from mcp.server.fastmcp import FastMCP

from ..utils.health import HealthChecker, SystemHealthMonitor
from ..utils.metrics import get_metrics_summary, MetricsCollector
from ..utils.alerting import alert_manager
from ..auth.jwt_handler import JWTAuthHandler, AuthenticationError

logger = logging.getLogger(__name__)


class MonitoringTools:
    """MCP tools for monitoring and observability."""
    
    def __init__(
        self,
        app: FastMCP,
        health_checker: HealthChecker,
        auth_handler: JWTAuthHandler
    ):
        self.app = app
        self.health_checker = health_checker
        self.auth_handler = auth_handler
        self.health_monitor = SystemHealthMonitor(health_checker)
        
        self._register_tools()
        
    def get_tool_functions(self):
        """Get tool functions for testing purposes."""
        return {
            'health_check': self._health_check_impl,
            'get_metrics': self._get_metrics_impl,
            'get_service_status': self._get_service_status_impl,
            'get_alerts': self._get_alerts_impl,
            'get_monitoring_summary': self._get_monitoring_summary_impl
        }
        
    def _register_tools(self):
        """Register all monitoring MCP tools."""
        
        @self.app.tool()
        async def health_check(auth_token: str = None) -> List[TextContent]:
            """
            Check the health status of the MCP server and its dependencies.
            
            Args:
                auth_token: JWT authentication token
                
            Returns:
                Health status information for all services
            """
            return await self._health_check_impl(auth_token)
    
    async def _health_check_impl(self, auth_token: str = None) -> List[TextContent]:
        """Implementation of health check tool."""
        try:
            # Validate authentication
            if auth_token:
                try:
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    logger.info(
                        "Health check requested",
                        extra={"user_id": user_context.user_id}
                    )
                except AuthenticationError:
                    return [TextContent(
                        type="text",
                        text="Authentication failed: Invalid or expired token"
                    )]
            
            # Get health status
            health_status = await self.health_checker.get_overall_health()
            
            # Format response
            status_text = f"Overall Status: {health_status['status'].upper()}\n\n"
            
            # Service details
            status_text += "Service Health:\n"
            for service_name, service_info in health_status['services'].items():
                status_icon = "✅" if service_info['status'] == 'healthy' else "❌"
                status_text += f"{status_icon} {service_name}: {service_info['status']}\n"
                
                if service_info['response_time_ms']:
                    status_text += f"   Response Time: {service_info['response_time_ms']:.2f}ms\n"
                
                if service_info['error']:
                    status_text += f"   Error: {service_info['error']}\n"
                
                status_text += "\n"
            
            # Metrics summary
            metrics = health_status['metrics']
            status_text += f"Metrics:\n"
            status_text += f"• Total Services: {metrics['total_services']}\n"
            status_text += f"• Healthy Services: {metrics['healthy_services']}\n"
            status_text += f"• Unhealthy Services: {metrics['unhealthy_services']}\n"
            
            if metrics['average_response_time_ms']:
                status_text += f"• Average Response Time: {metrics['average_response_time_ms']:.2f}ms\n"
            
            return [TextContent(type="text", text=status_text)]
            
        except Exception as e:
            logger.error(f"Health check failed: {e}")
            return [TextContent(
                type="text",
                text=f"Health check failed: {str(e)}"
            )]
        
        @self.app.tool()
        async def get_metrics(auth_token: str = None) -> List[TextContent]:
            """
            Get current system metrics and performance statistics.
            
            Args:
                auth_token: JWT authentication token
                
            Returns:
                Current metrics and performance data
            """
            return await self._get_metrics_impl(auth_token)
    
    async def _get_metrics_impl(self, auth_token: str = None) -> List[TextContent]:
        """Implementation of get metrics tool."""
        try:
            # Validate authentication
            if auth_token:
                try:
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    logger.info(
                        "Metrics requested",
                        extra={"user_id": user_context.user_id}
                    )
                except AuthenticationError:
                    return [TextContent(
                        type="text",
                        text="Authentication failed: Invalid or expired token"
                    )]
            
            # Get metrics summary
            metrics = get_metrics_summary()
            
            # Format metrics
            metrics_text = "System Metrics:\n\n"
            
            metrics_text += f"MCP Operations:\n"
            metrics_text += f"• Total Requests: {metrics['total_requests']}\n"
            metrics_text += f"• Active Connections: {metrics['active_connections']}\n"
            metrics_text += f"• Total Errors: {metrics['total_errors']}\n\n"
            
            metrics_text += f"Authentication:\n"
            metrics_text += f"• Auth Requests: {metrics['auth_requests']}\n"
            metrics_text += f"• Auth Failures: {metrics['auth_failures']}\n\n"
            
            metrics_text += f"Query Operations:\n"
            query_ops = metrics['query_operations']
            metrics_text += f"• Transaction History: {query_ops['transaction_history']}\n"
            metrics_text += f"• Transaction Search: {query_ops['transaction_search']}\n"
            metrics_text += f"• Account Analytics: {query_ops['account_analytics']}\n"
            metrics_text += f"• Transaction Limits: {query_ops['transaction_limits']}\n"
            metrics_text += f"• Query Errors: {query_ops['query_errors']}\n"
            
            return [TextContent(type="text", text=metrics_text)]
            
        except Exception as e:
            logger.error(f"Get metrics failed: {e}")
            return [TextContent(
                type="text",
                text=f"Failed to get metrics: {str(e)}"
            )]
        
        @self.app.tool()
        async def get_service_status(
            service_name: str = None,
            auth_token: str = None
        ) -> List[TextContent]:
            """
            Get detailed status information for backend services.
            
            Args:
                service_name: Specific service to check (account-service, transaction-service)
                auth_token: JWT authentication token
                
            Returns:
                Detailed service status information
            """
            return await self._get_service_status_impl(service_name, auth_token)
    
    async def _get_service_status_impl(self, service_name: str = None, auth_token: str = None) -> List[TextContent]:
        """Implementation of get service status tool."""
        try:
            # Validate authentication
            if auth_token:
                try:
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    logger.info(
                        "Service status requested",
                        extra={
                            "user_id": user_context.user_id,
                            "service_name": service_name
                        }
                    )
                except AuthenticationError:
                    return [TextContent(
                        type="text",
                        text="Authentication failed: Invalid or expired token"
                    )]
            
            if service_name:
                # Check specific service
                if service_name == "account-service":
                    result = await self.health_checker.check_account_service()
                elif service_name == "transaction-service":
                    result = await self.health_checker.check_transaction_service()
                else:
                    return [TextContent(
                        type="text",
                        text=f"Unknown service: {service_name}. Available services: account-service, transaction-service"
                    )]
                
                status_text = f"Service: {result.service}\n"
                status_text += f"Status: {result.status.value}\n"
                status_text += f"Response Time: {result.response_time_ms:.2f}ms\n"
                status_text += f"Timestamp: {result.timestamp.isoformat()}\n"
                
                if result.error:
                    status_text += f"Error: {result.error}\n"
                
                if result.details:
                    status_text += f"Details: {result.details}\n"
                
            else:
                # Check all services
                results = await self.health_checker.check_all_services(use_cache=False)
                
                status_text = "All Services Status:\n\n"
                for service_name, result in results.items():
                    status_icon = "✅" if result.status.value == 'healthy' else "❌"
                    status_text += f"{status_icon} {service_name}:\n"
                    status_text += f"   Status: {result.status.value}\n"
                    status_text += f"   Response Time: {result.response_time_ms:.2f}ms\n"
                    
                    if result.error:
                        status_text += f"   Error: {result.error}\n"
                    
                    status_text += "\n"
            
            return [TextContent(type="text", text=status_text)]
            
        except Exception as e:
            logger.error(f"Get service status failed: {e}")
            return [TextContent(
                type="text",
                text=f"Failed to get service status: {str(e)}"
            )]
        
        @self.app.tool()
        async def get_alerts(
            limit: int = 10,
            active_only: bool = False,
            auth_token: str = None
        ) -> List[TextContent]:
            """
            Get current alerts and alert history.
            
            Args:
                limit: Maximum number of alerts to return (default: 10)
                active_only: Return only active alerts (default: False)
                auth_token: JWT authentication token
                
            Returns:
                List of alerts with details
            """
            return await self._get_alerts_impl(limit, active_only, auth_token)
        
        @self.app.tool()
        async def get_monitoring_summary(auth_token: str = None) -> List[TextContent]:
            """
            Get comprehensive monitoring summary including health, metrics, and alerts.
            
            Args:
                auth_token: JWT authentication token
                
            Returns:
                Comprehensive monitoring dashboard summary
            """
            return await self._get_monitoring_summary_impl(auth_token)
    
    async def start_monitoring(self):
        """Start continuous health monitoring."""
        await self.health_monitor.start_monitoring()
        logger.info("Continuous health monitoring started")
    
    async def stop_monitoring(self):
        """Stop continuous health monitoring."""
        await self.health_monitor.stop_monitoring()
        logger.info("Continuous health monitoring stopped")
    
    async def _get_alerts_impl(self, limit: int = 10, active_only: bool = False, auth_token: str = None) -> List[TextContent]:
        """Implementation of get alerts tool."""
        try:
            # Validate authentication
            if auth_token:
                try:
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    logger.info(
                        "Alerts requested",
                        extra={
                            "user_id": user_context.user_id,
                            "limit": limit,
                            "active_only": active_only
                        }
                    )
                except AuthenticationError:
                    return [TextContent(
                        type="text",
                        text="Authentication failed: Invalid or expired token"
                    )]
            
            if active_only:
                alerts = alert_manager.get_active_alerts()
            else:
                alerts = alert_manager.get_alert_history(limit)
            
            if not alerts:
                return [TextContent(
                    type="text",
                    text="No alerts found" if not active_only else "No active alerts"
                )]
            
            alerts_text = f"{'Active Alerts' if active_only else 'Recent Alerts'} ({len(alerts)}):\n\n"
            
            for alert in alerts[-limit:]:  # Show most recent first
                severity_icon = {
                    "critical": "🔴",
                    "warning": "🟡", 
                    "info": "🔵"
                }.get(alert.severity.value, "⚪")
                
                alerts_text += f"{severity_icon} {alert.title}\n"
                alerts_text += f"   Type: {alert.type.value}\n"
                alerts_text += f"   Severity: {alert.severity.value}\n"
                alerts_text += f"   Time: {alert.timestamp.strftime('%Y-%m-%d %H:%M:%S UTC')}\n"
                alerts_text += f"   Source: {alert.source}\n"
                alerts_text += f"   Description: {alert.description}\n"
                
                if alert.resolved:
                    alerts_text += f"   ✅ Resolved at: {alert.resolved_at.strftime('%Y-%m-%d %H:%M:%S UTC')}\n"
                
                if alert.metadata:
                    alerts_text += f"   Metadata: {alert.metadata}\n"
                
                alerts_text += "\n"
            
            return [TextContent(type="text", text=alerts_text)]
            
        except Exception as e:
            logger.error(f"Get alerts failed: {e}")
            return [TextContent(
                type="text",
                text=f"Failed to get alerts: {str(e)}"
            )]
    
    async def _get_monitoring_summary_impl(self, auth_token: str = None) -> List[TextContent]:
        """Implementation of get monitoring summary tool."""
        try:
            # Validate authentication
            if auth_token:
                try:
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    logger.info(
                        "Monitoring summary requested",
                        extra={"user_id": user_context.user_id}
                    )
                except AuthenticationError:
                    return [TextContent(
                        type="text",
                        text="Authentication failed: Invalid or expired token"
                    )]
            
            # Get all monitoring data
            health_status = await self.health_checker.get_overall_health()
            metrics = get_metrics_summary()
            alert_stats = alert_manager.get_alert_stats()
            health_summary = self.health_monitor.get_health_summary()
            
            # Build comprehensive summary
            summary_text = "🔍 MCP Financial Server Monitoring Summary\n"
            summary_text += "=" * 50 + "\n\n"
            
            # Overall health
            status_icon = "✅" if health_status['status'] == 'healthy' else "❌"
            summary_text += f"{status_icon} Overall Status: {health_status['status'].upper()}\n\n"
            
            # Service health
            summary_text += "🏥 Service Health:\n"
            for service_name, service_info in health_status['services'].items():
                icon = "✅" if service_info['status'] == 'healthy' else "❌"
                summary_text += f"   {icon} {service_name}: {service_info['status']}\n"
            
            # Key metrics
            summary_text += f"\n📊 Key Metrics:\n"
            summary_text += f"   • Total Requests: {metrics['total_requests']}\n"
            summary_text += f"   • Active Connections: {metrics['active_connections']}\n"
            summary_text += f"   • Error Count: {metrics['total_errors']}\n"
            summary_text += f"   • Auth Success Rate: {((metrics['auth_requests'] - metrics['auth_failures']) / max(metrics['auth_requests'], 1) * 100):.1f}%\n"
            
            # Alert summary
            summary_text += f"\n🚨 Alert Summary (24h):\n"
            summary_text += f"   • Active Alerts: {alert_stats['active_alerts']}\n"
            summary_text += f"   • Total Alerts: {alert_stats['total_alerts_24h']}\n"
            
            if alert_stats['alerts_by_severity_24h']:
                summary_text += "   • By Severity: "
                severity_parts = []
                for severity, count in alert_stats['alerts_by_severity_24h'].items():
                    severity_parts.append(f"{severity}: {count}")
                summary_text += ", ".join(severity_parts) + "\n"
            
            # Uptime statistics
            if health_summary.get('uptime_stats'):
                summary_text += f"\n⏱️ Uptime Statistics:\n"
                for service, stats in health_summary['uptime_stats'].items():
                    summary_text += f"   • {service}: {stats['uptime_percentage']:.1f}%\n"
            
            summary_text += f"\n📅 Last Updated: {health_status['timestamp']}\n"
            
            return [TextContent(type="text", text=summary_text)]
            
        except Exception as e:
            logger.error(f"Get monitoring summary failed: {e}")
            return [TextContent(
                type="text",
                text=f"Failed to get monitoring summary: {str(e)}"
            )]