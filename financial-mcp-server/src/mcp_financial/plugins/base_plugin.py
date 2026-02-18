"""
Base plugin classes for extensible MCP tool registration.
"""

import logging
from abc import ABC, abstractmethod
from typing import Dict, Any, List, Optional, Callable
from dataclasses import dataclass
from mcp.server.fastmcp import FastMCP
from mcp.types import Tool, TextContent

logger = logging.getLogger(__name__)


@dataclass
class ToolMetadata:
    """Metadata for MCP tools."""
    name: str
    description: str
    version: str
    author: str
    category: str
    tags: List[str]
    requires_auth: bool = True
    permissions: List[str] = None
    
    def __post_init__(self):
        if self.permissions is None:
            self.permissions = []


class BasePlugin(ABC):
    """Base class for all MCP plugins."""
    
    def __init__(self, name: str, version: str = "1.0.0"):
        self.name = name
        self.version = version
        self.enabled = True
        self.logger = logging.getLogger(f"plugin.{name}")
        
    @abstractmethod
    async def initialize(self, context: Dict[str, Any]) -> None:
        """Initialize the plugin with context."""
        pass
        
    @abstractmethod
    async def cleanup(self) -> None:
        """Cleanup plugin resources."""
        pass
        
    def enable(self) -> None:
        """Enable the plugin."""
        self.enabled = True
        self.logger.info(f"Plugin {self.name} enabled")
        
    def disable(self) -> None:
        """Disable the plugin."""
        self.enabled = False
        self.logger.info(f"Plugin {self.name} disabled")
        
    @property
    def is_enabled(self) -> bool:
        """Check if plugin is enabled."""
        return self.enabled


class ToolPlugin(BasePlugin):
    """Base class for plugins that provide MCP tools."""
    
    def __init__(self, name: str, version: str = "1.0.0"):
        super().__init__(name, version)
        self.tools: Dict[str, ToolMetadata] = {}
        self.tool_functions: Dict[str, Callable] = {}
        
    @abstractmethod
    def get_tools(self) -> Dict[str, ToolMetadata]:
        """Get all tools provided by this plugin."""
        pass
        
    @abstractmethod
    async def register_tools(self, app: FastMCP, context: Dict[str, Any]) -> None:
        """Register tools with the MCP application."""
        pass
        
    def add_tool(self, metadata: ToolMetadata, func: Callable) -> None:
        """Add a tool to this plugin."""
        self.tools[metadata.name] = metadata
        self.tool_functions[metadata.name] = func
        self.logger.info(f"Added tool {metadata.name} to plugin {self.name}")
        
    def remove_tool(self, tool_name: str) -> None:
        """Remove a tool from this plugin."""
        if tool_name in self.tools:
            del self.tools[tool_name]
            del self.tool_functions[tool_name]
            self.logger.info(f"Removed tool {tool_name} from plugin {self.name}")
            
    def get_tool_metadata(self, tool_name: str) -> Optional[ToolMetadata]:
        """Get metadata for a specific tool."""
        return self.tools.get(tool_name)
        
    def get_tool_function(self, tool_name: str) -> Optional[Callable]:
        """Get function for a specific tool."""
        return self.tool_functions.get(tool_name)


class FinancialToolPlugin(ToolPlugin):
    """Base class for financial-specific tool plugins."""
    
    def __init__(self, name: str, version: str = "1.0.0"):
        super().__init__(name, version)
        self.auth_handler = None
        self.account_client = None
        self.transaction_client = None
        
    async def initialize(self, context: Dict[str, Any]) -> None:
        """Initialize with financial service context."""
        self.auth_handler = context.get('auth_handler')
        self.account_client = context.get('account_client')
        self.transaction_client = context.get('transaction_client')
        
        if not all([self.auth_handler, self.account_client, self.transaction_client]):
            raise ValueError(f"Plugin {self.name} requires auth_handler, account_client, and transaction_client")
            
        self.logger.info(f"Financial plugin {self.name} initialized with service clients")
        
    async def cleanup(self) -> None:
        """Cleanup financial plugin resources."""
        self.auth_handler = None
        self.account_client = None
        self.transaction_client = None
        self.logger.info(f"Financial plugin {self.name} cleaned up")
        
    def validate_auth_context(self, auth_token: str) -> Any:
        """Validate authentication context for financial operations."""
        if not self.auth_handler:
            raise RuntimeError("Auth handler not initialized")
        return self.auth_handler.extract_user_context(auth_token)
        
    def check_permissions(self, user_context: Any, required_permissions: List[str]) -> bool:
        """Check if user has required permissions."""
        user_permissions = getattr(user_context, 'permissions', [])
        return all(perm in user_permissions for perm in required_permissions)