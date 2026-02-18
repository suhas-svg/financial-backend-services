"""
Main MCP server implementation using FastMCP framework.
"""

import asyncio
import logging
from pathlib import Path
from typing import Dict, Any, Optional
from mcp.server.fastmcp import FastMCP
from mcp.server.models import InitializationOptions
from mcp.types import Tool, TextContent, InitializeRequest, InitializeResult

from .auth.jwt_handler import JWTAuthHandler
from .clients.account_client import AccountServiceClient
from .clients.transaction_client import TransactionServiceClient
from .config.settings import Settings
from .utils.logging import setup_logging
from .utils.metrics import setup_metrics
from .utils.health import HealthChecker
from .utils.alerting import alert_manager, WebhookAlertChannel, SlackAlertChannel
from .plugins.plugin_manager import PluginManager
from .protocol.versioning import VersionManager
from .protocol.compliance import ProtocolCompliance
from .protocol.validation import ProtocolValidator

logger = logging.getLogger(__name__)


class FinancialMCPServer:
    """Main MCP server for financial operations."""
    
    def __init__(self, settings: Optional[Settings] = None):
        self.settings = settings or Settings()
        self.app = FastMCP("Financial Services MCP")
        
        # Initialize protocol compliance components
        self.version_manager = VersionManager()
        self.protocol_compliance = ProtocolCompliance(self.version_manager)
        self.protocol_validator = ProtocolValidator()
        
        # Initialize components
        self.auth_handler = JWTAuthHandler(self.settings.jwt_secret)
        self.account_client = AccountServiceClient(
            base_url=self.settings.account_service_url,
            timeout=self.settings.http_timeout
        )
        self.transaction_client = TransactionServiceClient(
            base_url=self.settings.transaction_service_url,
            timeout=self.settings.http_timeout
        )
        
        # Initialize plugin manager
        self.plugin_manager = PluginManager(self.app)
        
        # Setup logging and metrics
        setup_logging(self.settings.log_level, self.settings.log_format)
        self.metrics_server = setup_metrics(
            port=self.settings.metrics_port,
            enabled=self.settings.metrics_enabled
        )
        
        # Initialize health checker
        self.health_checker = HealthChecker(
            account_service_url=self.settings.account_service_url,
            transaction_service_url=self.settings.transaction_service_url,
            timeout=self.settings.http_timeout
        )
        
        # Setup alerting
        self._setup_alerting()
        
    async def initialize(self, options: InitializationOptions) -> None:
        """Initialize the MCP server."""
        logger.info("Initializing Financial MCP Server")
        
        try:
            await self._setup_plugin_manager()
            await self._register_tools()
            await self._setup_monitoring()
            await self._load_plugins()
            logger.info("Financial MCP Server initialized successfully")
        except Exception as e:
            logger.error(f"Failed to initialize MCP server: {e}")
            raise
            
    async def _setup_plugin_manager(self) -> None:
        """Setup plugin manager with context."""
        context = {
            'auth_handler': self.auth_handler,
            'account_client': self.account_client,
            'transaction_client': self.transaction_client,
            'health_checker': self.health_checker,
            'settings': self.settings
        }
        
        self.plugin_manager.set_context(context)
        
        # Add default plugin paths
        plugin_paths = [
            Path("./plugins"),
            Path("./custom_plugins"),
            Path(self.settings.plugin_directory) if hasattr(self.settings, 'plugin_directory') else None
        ]
        
        for path in plugin_paths:
            if path and path.exists():
                self.plugin_manager.add_plugin_path(path)
                
        logger.info("Plugin manager setup completed")
        
    async def _load_plugins(self) -> None:
        """Load plugins from configured paths."""
        try:
            await self.plugin_manager.discover_and_load_plugins()
            
            # Log plugin statistics
            stats = self.plugin_manager.get_manager_stats()
            logger.info(f"Loaded {stats['total_plugins']} plugins with {stats['registry_stats']['total_tools']} tools")
            
        except Exception as e:
            logger.error(f"Failed to load plugins: {e}")
            # Don't fail server startup if plugins fail to load
            
    def negotiate_protocol_version(self, client_version: str) -> str:
        """Negotiate protocol version with client."""
        return self.version_manager.negotiate_version(client_version)
        
    def validate_protocol_compliance(self, request: InitializeRequest) -> InitializeResult:
        """Validate and create protocol-compliant initialize result."""
        # Validate request compliance
        issues = self.protocol_compliance.validate_initialize_request(request)
        
        if any(issue.severity == "error" for issue in issues):
            error_messages = [issue.message for issue in issues if issue.severity == "error"]
            raise ValueError(f"Protocol compliance errors: {'; '.join(error_messages)}")
            
        # Negotiate version
        negotiated_version = self.negotiate_protocol_version(request.protocolVersion)
        
        # Create compliant result
        result = InitializeResult(
            protocolVersion=negotiated_version,
            capabilities={
                "tools": {
                    "listChanged": True
                },
                "logging": {},
                "completion": self.version_manager.is_feature_supported(negotiated_version, "completion")
            },
            serverInfo={
                "name": "financial-mcp-server",
                "version": "1.0.0"
            }
        )
        
        # Validate result compliance
        result_issues = self.protocol_compliance.validate_initialize_result(result, request.protocolVersion)
        
        if any(issue.severity == "error" for issue in result_issues):
            error_messages = [issue.message for issue in result_issues if issue.severity == "error"]
            raise ValueError(f"Result compliance errors: {'; '.join(error_messages)}")
            
        return result
            
    async def _register_tools(self) -> None:
        """Register all financial tools."""
        logger.info("Registering MCP tools")
        
        # Import and register account tools
        from .tools.account_tools import AccountTools
        from .tools.transaction_tools import TransactionTools
        from .tools.query_tools import QueryTools
        from .tools.monitoring_tools import MonitoringTools
        
        # Initialize account tools
        self.account_tools = AccountTools(
            self.app,
            self.account_client,
            self.auth_handler
        )
        
        # Initialize transaction tools
        self.transaction_tools = TransactionTools(
            self.app,
            self.transaction_client,
            self.account_client,
            self.auth_handler
        )
        
        # Initialize query tools
        self.query_tools = QueryTools(
            self.app,
            self.account_client,
            self.transaction_client,
            self.auth_handler
        )
        
        # Initialize monitoring tools
        self.monitoring_tools = MonitoringTools(
            self.app,
            self.health_checker,
            self.auth_handler
        )
        
        logger.info("All MCP tools registered successfully")
        
    async def _setup_monitoring(self) -> None:
        """Setup monitoring and health checks."""
        logger.info("Setting up monitoring")
        
        # Start continuous health monitoring
        if hasattr(self, 'monitoring_tools'):
            await self.monitoring_tools.start_monitoring()
        
        logger.info("Monitoring setup completed")
    
    def _setup_alerting(self) -> None:
        """Setup alerting channels."""
        # Add webhook alert channel if configured
        webhook_url = getattr(self.settings, 'alert_webhook_url', None)
        if webhook_url:
            webhook_channel = WebhookAlertChannel(webhook_url)
            alert_manager.add_channel(webhook_channel)
            logger.info("Webhook alert channel configured")
        
        # Add Slack alert channel if configured
        slack_webhook = getattr(self.settings, 'slack_webhook_url', None)
        slack_channel = getattr(self.settings, 'slack_channel', '#alerts')
        if slack_webhook:
            slack_alert_channel = SlackAlertChannel(slack_webhook, slack_channel)
            alert_manager.add_channel(slack_alert_channel)
            logger.info("Slack alert channel configured")
    
    async def shutdown(self) -> None:
        """Shutdown the server and cleanup resources."""
        logger.info("Shutting down MCP server")
        
        # Cleanup plugins
        if hasattr(self, 'plugin_manager'):
            await self.plugin_manager.cleanup()
        
        # Stop monitoring
        if hasattr(self, 'monitoring_tools'):
            await self.monitoring_tools.stop_monitoring()
        
        # Close health checker
        if hasattr(self, 'health_checker'):
            await self.health_checker.close()
        
        # Close alert manager
        await alert_manager.close()
        
        logger.info("MCP server shutdown completed")
        
    def get_app(self) -> FastMCP:
        """Get the FastMCP application instance."""
        return self.app


async def create_server(settings: Optional[Settings] = None) -> FinancialMCPServer:
    """Create and initialize the MCP server."""
    server = FinancialMCPServer(settings)
    await server.initialize(InitializationOptions())
    return server  
      
    def get_plugin_manager(self) -> PluginManager:
        """Get the plugin manager instance."""
        return self.plugin_manager
        
    def get_version_manager(self) -> VersionManager:
        """Get the version manager instance."""
        return self.version_manager
        
    def get_protocol_compliance(self) -> ProtocolCompliance:
        """Get the protocol compliance validator."""
        return self.protocol_compliance
        
    def get_registry_stats(self) -> Dict[str, Any]:
        """Get tool registry statistics."""
        return self.plugin_manager.get_registry().get_registry_stats()
        
    async def reload_plugin(self, plugin_name: str) -> bool:
        """Reload a specific plugin."""
        return await self.plugin_manager.reload_plugin(plugin_name)
        
    def list_available_tools(self) -> List[str]:
        """List all available tool names."""
        registry = self.plugin_manager.get_registry()
        return [reg.metadata.name for reg in registry.list_tools(enabled_only=True)]
        
    def get_tool_info(self, tool_name: str) -> Optional[Dict[str, Any]]:
        """Get detailed information about a tool."""
        registry = self.plugin_manager.get_registry()
        registration = registry.get_tool(tool_name)
        
        if not registration:
            return None
            
        return {
            "name": registration.metadata.name,
            "description": registration.metadata.description,
            "version": registration.metadata.version,
            "author": registration.metadata.author,
            "category": registration.metadata.category,
            "tags": registration.metadata.tags,
            "requires_auth": registration.metadata.requires_auth,
            "permissions": registration.metadata.permissions,
            "plugin_name": registration.plugin_name,
            "registered_at": registration.registered_at.isoformat(),
            "enabled": registration.enabled
        }
        
    def get_compliance_report(self) -> Dict[str, Any]:
        """Get comprehensive protocol compliance report."""
        return {
            "protocol_versions": {
                "current": self.version_manager.get_latest_version(),
                "supported": self.version_manager.get_supported_versions()
            },
            "capabilities": {
                "tools": True,
                "logging": True,
                "completion": True,
                "plugins": True,
                "extensibility": True
            },
            "plugin_stats": self.get_registry_stats(),
            "server_info": {
                "name": "financial-mcp-server",
                "version": "1.0.0",
                "features": [
                    "protocol_compliance",
                    "plugin_architecture", 
                    "version_negotiation",
                    "backward_compatibility",
                    "extensible_tools"
                ]
            }
        }