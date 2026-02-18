"""
Plugin system for extensible MCP tool registration.
"""

from .plugin_manager import PluginManager
from .base_plugin import BasePlugin, ToolPlugin
from .registry import ToolRegistry

__all__ = [
    'PluginManager',
    'BasePlugin', 
    'ToolPlugin',
    'ToolRegistry'
]