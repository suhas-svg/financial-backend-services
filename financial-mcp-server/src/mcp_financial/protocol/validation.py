"""
Protocol validation utilities for MCP compliance.
"""

import logging
import json
from typing import Dict, List, Optional, Any, Union
from jsonschema import validate, ValidationError, Draft7Validator
from mcp.types import Tool, TextContent

logger = logging.getLogger(__name__)


class ProtocolValidator:
    """Validates MCP protocol messages and data structures."""
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
        self._initialize_schemas()
        
    def _initialize_schemas(self) -> None:
        """Initialize JSON schemas for validation."""
        
        # MCP Initialize Request Schema
        self.initialize_request_schema = {
            "type": "object",
            "required": ["protocolVersion", "capabilities", "clientInfo"],
            "properties": {
                "protocolVersion": {"type": "string"},
                "capabilities": {"type": "object"},
                "clientInfo": {
                    "type": "object",
                    "required": ["name"],
                    "properties": {
                        "name": {"type": "string"},
                        "version": {"type": "string"}
                    }
                }
            }
        }
        
        # MCP Initialize Result Schema
        self.initialize_result_schema = {
            "type": "object",
            "required": ["protocolVersion", "capabilities", "serverInfo"],
            "properties": {
                "protocolVersion": {"type": "string"},
                "capabilities": {"type": "object"},
                "serverInfo": {
                    "type": "object",
                    "required": ["name"],
                    "properties": {
                        "name": {"type": "string"},
                        "version": {"type": "string"}
                    }
                }
            }
        }
        
        # MCP Tool Schema
        self.tool_schema = {
            "type": "object",
            "required": ["name", "description", "inputSchema"],
            "properties": {
                "name": {"type": "string"},
                "description": {"type": "string"},
                "inputSchema": {
                    "type": "object",
                    "required": ["type"],
                    "properties": {
                        "type": {"type": "string"},
                        "properties": {"type": "object"},
                        "required": {
                            "type": "array",
                            "items": {"type": "string"}
                        }
                    }
                }
            }
        }
        
        # MCP Call Tool Request Schema
        self.call_tool_request_schema = {
            "type": "object",
            "required": ["name"],
            "properties": {
                "name": {"type": "string"},
                "arguments": {"type": "object"}
            }
        }
        
        # MCP Call Tool Result Schema
        self.call_tool_result_schema = {
            "type": "object",
            "required": ["content"],
            "properties": {
                "content": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "required": ["type"],
                        "properties": {
                            "type": {"type": "string"},
                            "text": {"type": "string"}
                        }
                    }
                },
                "isError": {"type": "boolean"}
            }
        }
        
    def validate_json_schema(self, data: Dict[str, Any], schema: Dict[str, Any]) -> List[str]:
        """Validate data against JSON schema."""
        errors = []
        
        try:
            validate(instance=data, schema=schema)
        except ValidationError as e:
            errors.append(f"Schema validation error: {e.message}")
            if e.path:
                errors.append(f"Error path: {' -> '.join(str(p) for p in e.path)}")
                
        return errors
        
    def validate_initialize_request(self, data: Dict[str, Any]) -> List[str]:
        """Validate MCP initialize request."""
        return self.validate_json_schema(data, self.initialize_request_schema)
        
    def validate_initialize_result(self, data: Dict[str, Any]) -> List[str]:
        """Validate MCP initialize result."""
        return self.validate_json_schema(data, self.initialize_result_schema)
        
    def validate_tool_definition(self, data: Dict[str, Any]) -> List[str]:
        """Validate MCP tool definition."""
        errors = self.validate_json_schema(data, self.tool_schema)
        
        # Additional validation for tool names
        if 'name' in data:
            name_errors = self.validate_tool_name(data['name'])
            errors.extend(name_errors)
            
        # Validate input schema is valid JSON Schema
        if 'inputSchema' in data:
            schema_errors = self.validate_input_schema(data['inputSchema'])
            errors.extend(schema_errors)
            
        return errors
        
    def validate_tool_name(self, name: str) -> List[str]:
        """Validate tool name format."""
        errors = []
        
        if not name:
            errors.append("Tool name cannot be empty")
        elif not isinstance(name, str):
            errors.append("Tool name must be a string")
        else:
            # Tool names should be valid identifiers
            import re
            if not re.match(r'^[a-zA-Z][a-zA-Z0-9_]*$', name):
                errors.append("Tool name must start with a letter and contain only letters, numbers, and underscores")
                
            # Check length
            if len(name) > 100:
                errors.append("Tool name should not exceed 100 characters")
                
        return errors
        
    def validate_input_schema(self, schema: Dict[str, Any]) -> List[str]:
        """Validate tool input schema is valid JSON Schema."""
        errors = []
        
        try:
            # Use Draft7Validator to validate the schema itself
            Draft7Validator.check_schema(schema)
        except Exception as e:
            errors.append(f"Invalid JSON Schema: {str(e)}")
            
        # Additional checks for MCP-specific requirements
        if schema.get('type') == 'object':
            if 'properties' not in schema:
                errors.append("Object schemas should define properties")
            elif not isinstance(schema['properties'], dict):
                errors.append("Properties must be a dictionary")
                
        return errors
        
    def validate_call_tool_request(self, data: Dict[str, Any]) -> List[str]:
        """Validate MCP call tool request."""
        return self.validate_json_schema(data, self.call_tool_request_schema)
        
    def validate_call_tool_result(self, data: Dict[str, Any]) -> List[str]:
        """Validate MCP call tool result."""
        errors = self.validate_json_schema(data, self.call_tool_result_schema)
        
        # Additional validation for content items
        if 'content' in data and isinstance(data['content'], list):
            for i, content_item in enumerate(data['content']):
                content_errors = self.validate_content_item(content_item, f"content[{i}]")
                errors.extend(content_errors)
                
        return errors
        
    def validate_content_item(self, content: Dict[str, Any], context: str = "content") -> List[str]:
        """Validate MCP content item."""
        errors = []
        
        if not isinstance(content, dict):
            errors.append(f"{context} must be an object")
            return errors
            
        if 'type' not in content:
            errors.append(f"{context} must have a type field")
        elif content['type'] not in ['text', 'image', 'resource']:
            errors.append(f"{context} has invalid type: {content['type']}")
            
        # Type-specific validation
        if content.get('type') == 'text':
            if 'text' not in content:
                errors.append(f"{context} text content must have text field")
            elif not isinstance(content['text'], str):
                errors.append(f"{context} text field must be a string")
                
        elif content.get('type') == 'image':
            if 'data' not in content and 'url' not in content:
                errors.append(f"{context} image content must have data or url field")
                
        elif content.get('type') == 'resource':
            if 'uri' not in content:
                errors.append(f"{context} resource content must have uri field")
                
        return errors
        
    def validate_tool_arguments(self, arguments: Dict[str, Any], input_schema: Dict[str, Any]) -> List[str]:
        """Validate tool arguments against input schema."""
        errors = []
        
        try:
            validate(instance=arguments, schema=input_schema)
        except ValidationError as e:
            errors.append(f"Argument validation error: {e.message}")
            if e.path:
                errors.append(f"Error path: {' -> '.join(str(p) for p in e.path)}")
                
        return errors
        
    def validate_protocol_message(self, message: Dict[str, Any], message_type: str) -> List[str]:
        """Validate any MCP protocol message."""
        errors = []
        
        if message_type == "initialize_request":
            errors = self.validate_initialize_request(message)
        elif message_type == "initialize_result":
            errors = self.validate_initialize_result(message)
        elif message_type == "call_tool_request":
            errors = self.validate_call_tool_request(message)
        elif message_type == "call_tool_result":
            errors = self.validate_call_tool_result(message)
        else:
            errors.append(f"Unknown message type: {message_type}")
            
        return errors
        
    def validate_error_response(self, error_data: Dict[str, Any]) -> List[str]:
        """Validate MCP error response format."""
        errors = []
        
        required_fields = ['success', 'error_code', 'error_message']
        for field in required_fields:
            if field not in error_data:
                errors.append(f"Error response missing required field: {field}")
                
        if 'success' in error_data and error_data['success'] is not False:
            errors.append("Error response success field must be false")
            
        if 'error_code' in error_data and not isinstance(error_data['error_code'], str):
            errors.append("Error code must be a string")
            
        if 'error_message' in error_data and not isinstance(error_data['error_message'], str):
            errors.append("Error message must be a string")
            
        return errors
        
    def validate_success_response(self, response_data: Dict[str, Any]) -> List[str]:
        """Validate MCP success response format."""
        errors = []
        
        if 'success' not in response_data:
            errors.append("Response missing success field")
        elif response_data['success'] is not True:
            errors.append("Success response success field must be true")
            
        return errors
        
    def create_validation_report(self, 
                               data: Dict[str, Any], 
                               message_type: str,
                               additional_validations: Optional[List[str]] = None) -> Dict[str, Any]:
        """Create comprehensive validation report."""
        errors = self.validate_protocol_message(data, message_type)
        
        if additional_validations:
            errors.extend(additional_validations)
            
        report = {
            "valid": len(errors) == 0,
            "message_type": message_type,
            "error_count": len(errors),
            "errors": errors,
            "data_size": len(json.dumps(data)) if data else 0
        }
        
        return report