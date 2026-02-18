"""
Tool registry for managing MCP tools and their metadata.
"""

import logging
from typing import Dict, List, Optional, Set, Any
from dataclasses import dataclass, field
from datetime import datetime
from .base_plugin import ToolMetadata, ToolPlugin

logger = logging.getLogger(__name__)


@dataclass
class ToolRegistration:
    """Registration information for an MCP tool."""
    metadata: ToolMetadata
    plugin_name: str
    registered_at: datetime = field(default_factory=datetime.utcnow)
    enabled: bool = True


class ToolRegistry:
    """Registry for managing MCP tools and their metadata."""
    
    def __init__(self):
        self.tools: Dict[str, ToolRegistration] = {}
        self.plugins: Dict[str, ToolPlugin] = {}
        self.categories: Dict[str, Set[str]] = {}
        self.tags: Dict[str, Set[str]] = {}
        self.logger = logging.getLogger(__name__)
        
    def register_plugin(self, plugin: ToolPlugin) -> None:
        """Register a plugin and its tools."""
        if plugin.name in self.plugins:
            self.logger.warning(f"Plugin {plugin.name} already registered, updating")
            
        self.plugins[plugin.name] = plugin
        
        # Register all tools from the plugin
        for tool_name, metadata in plugin.get_tools().items():
            self.register_tool(metadata, plugin.name)
            
        self.logger.info(f"Registered plugin {plugin.name} with {len(plugin.get_tools())} tools")
        
    def unregister_plugin(self, plugin_name: str) -> None:
        """Unregister a plugin and all its tools."""
        if plugin_name not in self.plugins:
            self.logger.warning(f"Plugin {plugin_name} not found for unregistration")
            return
            
        # Remove all tools from this plugin
        tools_to_remove = [
            tool_name for tool_name, registration in self.tools.items()
            if registration.plugin_name == plugin_name
        ]
        
        for tool_name in tools_to_remove:
            self.unregister_tool(tool_name)
            
        del self.plugins[plugin_name]
        self.logger.info(f"Unregistered plugin {plugin_name} and {len(tools_to_remove)} tools")
        
    def register_tool(self, metadata: ToolMetadata, plugin_name: str) -> None:
        """Register a single tool."""
        if metadata.name in self.tools:
            self.logger.warning(f"Tool {metadata.name} already registered, updating")
            
        registration = ToolRegistration(
            metadata=metadata,
            plugin_name=plugin_name
        )
        
        self.tools[metadata.name] = registration
        
        # Update category index
        if metadata.category not in self.categories:
            self.categories[metadata.category] = set()
        self.categories[metadata.category].add(metadata.name)
        
        # Update tag index
        for tag in metadata.tags:
            if tag not in self.tags:
                self.tags[tag] = set()
            self.tags[tag].add(metadata.name)
            
        self.logger.info(f"Registered tool {metadata.name} from plugin {plugin_name}")
        
    def unregister_tool(self, tool_name: str) -> None:
        """Unregister a single tool."""
        if tool_name not in self.tools:
            self.logger.warning(f"Tool {tool_name} not found for unregistration")
            return
            
        registration = self.tools[tool_name]
        metadata = registration.metadata
        
        # Remove from category index
        if metadata.category in self.categories:
            self.categories[metadata.category].discard(tool_name)
            if not self.categories[metadata.category]:
                del self.categories[metadata.category]
                
        # Remove from tag index
        for tag in metadata.tags:
            if tag in self.tags:
                self.tags[tag].discard(tool_name)
                if not self.tags[tag]:
                    del self.tags[tag]
                    
        del self.tools[tool_name]
        self.logger.info(f"Unregistered tool {tool_name}")
        
    def get_tool(self, tool_name: str) -> Optional[ToolRegistration]:
        """Get tool registration by name."""
        return self.tools.get(tool_name)
        
    def get_tool_metadata(self, tool_name: str) -> Optional[ToolMetadata]:
        """Get tool metadata by name."""
        registration = self.get_tool(tool_name)
        return registration.metadata if registration else None
        
    def list_tools(self, 
                   category: Optional[str] = None,
                   tag: Optional[str] = None,
                   enabled_only: bool = True) -> List[ToolRegistration]:
        """List tools with optional filtering."""
        tools = list(self.tools.values())
        
        if enabled_only:
            tools = [t for t in tools if t.enabled]
            
        if category:
            tools = [t for t in tools if t.metadata.category == category]
            
        if tag:
            tools = [t for t in tools if tag in t.metadata.tags]
            
        return tools
        
    def get_categories(self) -> List[str]:
        """Get all available categories."""
        return list(self.categories.keys())
        
    def get_tags(self) -> List[str]:
        """Get all available tags."""
        return list(self.tags.keys())
        
    def get_tools_by_category(self, category: str) -> List[str]:
        """Get tool names by category."""
        return list(self.categories.get(category, set()))
        
    def get_tools_by_tag(self, tag: str) -> List[str]:
        """Get tool names by tag."""
        return list(self.tags.get(tag, set()))
        
    def enable_tool(self, tool_name: str) -> bool:
        """Enable a tool."""
        if tool_name in self.tools:
            self.tools[tool_name].enabled = True
            self.logger.info(f"Enabled tool {tool_name}")
            return True
        return False
        
    def disable_tool(self, tool_name: str) -> bool:
        """Disable a tool."""
        if tool_name in self.tools:
            self.tools[tool_name].enabled = False
            self.logger.info(f"Disabled tool {tool_name}")
            return True
        return False
        
    def get_plugin(self, plugin_name: str) -> Optional[ToolPlugin]:
        """Get plugin by name."""
        return self.plugins.get(plugin_name)
        
    def list_plugins(self) -> List[str]:
        """List all registered plugin names."""
        return list(self.plugins.keys())
        
    def get_plugin_tools(self, plugin_name: str) -> List[str]:
        """Get all tool names from a specific plugin."""
        return [
            tool_name for tool_name, registration in self.tools.items()
            if registration.plugin_name == plugin_name
        ]
        
    def get_registry_stats(self) -> Dict[str, Any]:
        """Get registry statistics."""
        enabled_tools = sum(1 for t in self.tools.values() if t.enabled)
        
        return {
            "total_plugins": len(self.plugins),
            "total_tools": len(self.tools),
            "enabled_tools": enabled_tools,
            "disabled_tools": len(self.tools) - enabled_tools,
            "categories": len(self.categories),
            "tags": len(self.tags),
            "tools_by_category": {
                category: len(tools) 
                for category, tools in self.categories.items()
            }
        }
        
    def validate_tool_name(self, tool_name: str) -> bool:
        """Validate tool name format."""
        # Tool names should be alphanumeric with underscores
        import re
        return bool(re.match(r'^[a-zA-Z][a-zA-Z0-9_]*$', tool_name))
        
    def check_tool_conflicts(self, metadata: ToolMetadata) -> List[str]:
        """Check for potential conflicts with existing tools."""
        conflicts = []
        
        if metadata.name in self.tools:
            existing = self.tools[metadata.name]
            conflicts.append(f"Tool name '{metadata.name}' already exists in plugin '{existing.plugin_name}'")
            
        return conflicts