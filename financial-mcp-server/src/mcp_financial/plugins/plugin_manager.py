"""
Plugin manager for loading and managing MCP tool plugins.
"""

import logging
import importlib
import inspect
from pathlib import Path
from typing import Dict, List, Optional, Any, Type
from mcp.server.fastmcp import FastMCP

from .base_plugin import BasePlugin, ToolPlugin
from .registry import ToolRegistry

logger = logging.getLogger(__name__)


class PluginManager:
    """Manager for loading and managing MCP tool plugins."""
    
    def __init__(self, app: FastMCP):
        self.app = app
        self.registry = ToolRegistry()
        self.loaded_plugins: Dict[str, BasePlugin] = {}
        self.plugin_paths: List[Path] = []
        self.context: Dict[str, Any] = {}
        self.logger = logging.getLogger(__name__)
        
    def add_plugin_path(self, path: Path) -> None:
        """Add a path to search for plugins."""
        if path.exists() and path.is_dir():
            self.plugin_paths.append(path)
            self.logger.info(f"Added plugin path: {path}")
        else:
            self.logger.warning(f"Plugin path does not exist: {path}")
            
    def set_context(self, context: Dict[str, Any]) -> None:
        """Set context for plugin initialization."""
        self.context = context
        self.logger.info("Plugin context updated")
        
    async def load_plugin_from_module(self, module_name: str, plugin_class_name: str) -> Optional[BasePlugin]:
        """Load a plugin from a module."""
        try:
            module = importlib.import_module(module_name)
            plugin_class = getattr(module, plugin_class_name)
            
            if not issubclass(plugin_class, BasePlugin):
                self.logger.error(f"Class {plugin_class_name} is not a BasePlugin subclass")
                return None
                
            plugin = plugin_class()
            await plugin.initialize(self.context)
            
            self.loaded_plugins[plugin.name] = plugin
            
            if isinstance(plugin, ToolPlugin):
                self.registry.register_plugin(plugin)
                await plugin.register_tools(self.app, self.context)
                
            self.logger.info(f"Loaded plugin {plugin.name} from {module_name}")
            return plugin
            
        except Exception as e:
            self.logger.error(f"Failed to load plugin {plugin_class_name} from {module_name}: {e}")
            return None
            
    async def load_plugin_from_file(self, file_path: Path) -> Optional[BasePlugin]:
        """Load a plugin from a Python file."""
        try:
            spec = importlib.util.spec_from_file_location("plugin_module", file_path)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            
            # Find plugin classes in the module
            plugin_classes = []
            for name, obj in inspect.getmembers(module):
                if (inspect.isclass(obj) and 
                    issubclass(obj, BasePlugin) and 
                    obj != BasePlugin and 
                    obj != ToolPlugin):
                    plugin_classes.append(obj)
                    
            if not plugin_classes:
                self.logger.warning(f"No plugin classes found in {file_path}")
                return None
                
            if len(plugin_classes) > 1:
                self.logger.warning(f"Multiple plugin classes found in {file_path}, using first one")
                
            plugin_class = plugin_classes[0]
            plugin = plugin_class()
            await plugin.initialize(self.context)
            
            self.loaded_plugins[plugin.name] = plugin
            
            if isinstance(plugin, ToolPlugin):
                self.registry.register_plugin(plugin)
                await plugin.register_tools(self.app, self.context)
                
            self.logger.info(f"Loaded plugin {plugin.name} from {file_path}")
            return plugin
            
        except Exception as e:
            self.logger.error(f"Failed to load plugin from {file_path}: {e}")
            return None
            
    async def discover_and_load_plugins(self) -> None:
        """Discover and load plugins from configured paths."""
        for plugin_path in self.plugin_paths:
            self.logger.info(f"Discovering plugins in {plugin_path}")
            
            for file_path in plugin_path.glob("*.py"):
                if file_path.name.startswith("__"):
                    continue
                    
                await self.load_plugin_from_file(file_path)
                
    async def unload_plugin(self, plugin_name: str) -> bool:
        """Unload a plugin."""
        if plugin_name not in self.loaded_plugins:
            self.logger.warning(f"Plugin {plugin_name} not found for unloading")
            return False
            
        plugin = self.loaded_plugins[plugin_name]
        
        try:
            await plugin.cleanup()
            
            if isinstance(plugin, ToolPlugin):
                self.registry.unregister_plugin(plugin_name)
                
            del self.loaded_plugins[plugin_name]
            self.logger.info(f"Unloaded plugin {plugin_name}")
            return True
            
        except Exception as e:
            self.logger.error(f"Failed to unload plugin {plugin_name}: {e}")
            return False
            
    async def reload_plugin(self, plugin_name: str) -> bool:
        """Reload a plugin."""
        if plugin_name not in self.loaded_plugins:
            self.logger.warning(f"Plugin {plugin_name} not found for reloading")
            return False
            
        # Store plugin info before unloading
        plugin = self.loaded_plugins[plugin_name]
        plugin_module = plugin.__class__.__module__
        plugin_class_name = plugin.__class__.__name__
        
        # Unload the plugin
        if not await self.unload_plugin(plugin_name):
            return False
            
        # Reload the module
        try:
            module = importlib.import_module(plugin_module)
            importlib.reload(module)
            
            # Load the plugin again
            return await self.load_plugin_from_module(plugin_module, plugin_class_name) is not None
            
        except Exception as e:
            self.logger.error(f"Failed to reload plugin {plugin_name}: {e}")
            return False
            
    def get_plugin(self, plugin_name: str) -> Optional[BasePlugin]:
        """Get a loaded plugin by name."""
        return self.loaded_plugins.get(plugin_name)
        
    def list_plugins(self) -> List[str]:
        """List all loaded plugin names."""
        return list(self.loaded_plugins.keys())
        
    def get_plugin_info(self, plugin_name: str) -> Optional[Dict[str, Any]]:
        """Get information about a plugin."""
        plugin = self.get_plugin(plugin_name)
        if not plugin:
            return None
            
        info = {
            "name": plugin.name,
            "version": plugin.version,
            "enabled": plugin.is_enabled,
            "type": plugin.__class__.__name__
        }
        
        if isinstance(plugin, ToolPlugin):
            info.update({
                "tools": list(plugin.get_tools().keys()),
                "tool_count": len(plugin.get_tools())
            })
            
        return info
        
    def enable_plugin(self, plugin_name: str) -> bool:
        """Enable a plugin."""
        plugin = self.get_plugin(plugin_name)
        if plugin:
            plugin.enable()
            return True
        return False
        
    def disable_plugin(self, plugin_name: str) -> bool:
        """Disable a plugin."""
        plugin = self.get_plugin(plugin_name)
        if plugin:
            plugin.disable()
            return True
        return False
        
    def get_registry(self) -> ToolRegistry:
        """Get the tool registry."""
        return self.registry
        
    async def cleanup(self) -> None:
        """Cleanup all plugins."""
        for plugin_name in list(self.loaded_plugins.keys()):
            await self.unload_plugin(plugin_name)
            
        self.logger.info("All plugins cleaned up")
        
    def get_manager_stats(self) -> Dict[str, Any]:
        """Get plugin manager statistics."""
        enabled_plugins = sum(1 for p in self.loaded_plugins.values() if p.is_enabled)
        tool_plugins = sum(1 for p in self.loaded_plugins.values() if isinstance(p, ToolPlugin))
        
        return {
            "total_plugins": len(self.loaded_plugins),
            "enabled_plugins": enabled_plugins,
            "disabled_plugins": len(self.loaded_plugins) - enabled_plugins,
            "tool_plugins": tool_plugins,
            "plugin_paths": [str(p) for p in self.plugin_paths],
            "registry_stats": self.registry.get_registry_stats()
        }