"""
MCP protocol compliance validation and enforcement.
"""

import logging
from typing import Dict, List, Optional, Any, Set
from dataclasses import dataclass
from datetime import datetime, timezone
from mcp.types import (
    InitializeRequest, InitializeResult,
    ListToolsRequest, ListToolsResult,
    CallToolRequest, CallToolResult,
    Tool, TextContent
)

from .versioning import VersionManager

logger = logging.getLogger(__name__)


@dataclass
class ComplianceIssue:
    """Represents a protocol compliance issue."""
    severity: str  # "error", "warning", "info"
    code: str
    message: str
    details: Optional[Dict[str, Any]] = None


class ProtocolCompliance:
    """Validates and enforces MCP protocol compliance."""
    
    def __init__(self, version_manager: VersionManager):
        self.version_manager = version_manager
        self.logger = logging.getLogger(__name__)
        
    def validate_initialize_request(self, request: InitializeRequest) -> List[ComplianceIssue]:
        """Validate MCP initialize request compliance."""
        issues = []
        
        # Check protocol version
        if not hasattr(request, 'protocolVersion') or not request.protocolVersion:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_PROTOCOL_VERSION",
                message="Initialize request must include protocolVersion"
            ))
        elif not self.version_manager.is_version_supported(request.protocolVersion):
            issues.append(ComplianceIssue(
                severity="warning",
                code="UNSUPPORTED_PROTOCOL_VERSION",
                message=f"Protocol version {request.protocolVersion} is not supported",
                details={"supported_versions": self.version_manager.get_supported_versions()}
            ))
            
        # Check capabilities
        if not hasattr(request, 'capabilities') or request.capabilities is None:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_CAPABILITIES",
                message="Initialize request must include capabilities"
            ))
        elif not isinstance(request.capabilities, dict):
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_CAPABILITIES_TYPE",
                message="Capabilities must be a dictionary"
            ))
            
        # Check client info
        if not hasattr(request, 'clientInfo') or request.clientInfo is None:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_CLIENT_INFO",
                message="Initialize request must include clientInfo"
            ))
        elif isinstance(request.clientInfo, dict):
            if 'name' not in request.clientInfo:
                issues.append(ComplianceIssue(
                    severity="error",
                    code="MISSING_CLIENT_NAME",
                    message="Client info must include name"
                ))
            if 'version' not in request.clientInfo:
                issues.append(ComplianceIssue(
                    severity="warning",
                    code="MISSING_CLIENT_VERSION",
                    message="Client info should include version"
                ))
                
        return issues
        
    def validate_initialize_result(self, result: InitializeResult, request_version: str) -> List[ComplianceIssue]:
        """Validate MCP initialize result compliance."""
        issues = []
        
        # Check protocol version
        if not hasattr(result, 'protocolVersion') or not result.protocolVersion:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_RESULT_PROTOCOL_VERSION",
                message="Initialize result must include protocolVersion"
            ))
        elif result.protocolVersion != request_version:
            # Check if negotiated version is valid
            negotiated = self.version_manager.negotiate_version(request_version)
            if result.protocolVersion != negotiated:
                issues.append(ComplianceIssue(
                    severity="error",
                    code="INVALID_NEGOTIATED_VERSION",
                    message=f"Negotiated version {result.protocolVersion} is invalid"
                ))
                
        # Check capabilities
        if not hasattr(result, 'capabilities') or result.capabilities is None:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_RESULT_CAPABILITIES",
                message="Initialize result must include capabilities"
            ))
        elif not isinstance(result.capabilities, dict):
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_RESULT_CAPABILITIES_TYPE",
                message="Result capabilities must be a dictionary"
            ))
            
        # Check server info
        if not hasattr(result, 'serverInfo') or result.serverInfo is None:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_SERVER_INFO",
                message="Initialize result must include serverInfo"
            ))
        elif isinstance(result.serverInfo, dict):
            if 'name' not in result.serverInfo:
                issues.append(ComplianceIssue(
                    severity="error",
                    code="MISSING_SERVER_NAME",
                    message="Server info must include name"
                ))
            if 'version' not in result.serverInfo:
                issues.append(ComplianceIssue(
                    severity="warning",
                    code="MISSING_SERVER_VERSION",
                    message="Server info should include version"
                ))
                
        return issues
        
    def validate_list_tools_result(self, result: ListToolsResult) -> List[ComplianceIssue]:
        """Validate MCP list tools result compliance."""
        issues = []
        
        # Check tools array
        if not hasattr(result, 'tools') or result.tools is None:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_TOOLS_ARRAY",
                message="List tools result must include tools array"
            ))
        elif not isinstance(result.tools, list):
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_TOOLS_TYPE",
                message="Tools must be an array"
            ))
        else:
            # Validate each tool
            for i, tool in enumerate(result.tools):
                tool_issues = self.validate_tool_definition(tool, f"tools[{i}]")
                issues.extend(tool_issues)
                
        return issues
        
    def validate_tool_definition(self, tool: Tool, context: str = "tool") -> List[ComplianceIssue]:
        """Validate MCP tool definition compliance."""
        issues = []
        
        # Check required fields
        if not hasattr(tool, 'name') or not tool.name:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_TOOL_NAME",
                message=f"{context} must have a name",
                details={"context": context}
            ))
        elif not isinstance(tool.name, str):
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_TOOL_NAME_TYPE",
                message=f"{context} name must be a string",
                details={"context": context}
            ))
            
        if not hasattr(tool, 'description') or not tool.description:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_TOOL_DESCRIPTION",
                message=f"{context} must have a description",
                details={"context": context}
            ))
        elif not isinstance(tool.description, str):
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_TOOL_DESCRIPTION_TYPE",
                message=f"{context} description must be a string",
                details={"context": context}
            ))
            
        # Check input schema
        if not hasattr(tool, 'inputSchema') or tool.inputSchema is None:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_INPUT_SCHEMA",
                message=f"{context} must have an inputSchema",
                details={"context": context}
            ))
        elif not isinstance(tool.inputSchema, dict):
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_INPUT_SCHEMA_TYPE",
                message=f"{context} inputSchema must be a dictionary",
                details={"context": context}
            ))
        else:
            # Validate JSON schema structure
            schema_issues = self.validate_json_schema(tool.inputSchema, f"{context}.inputSchema")
            issues.extend(schema_issues)
            
        return issues
        
    def validate_json_schema(self, schema: Dict[str, Any], context: str) -> List[ComplianceIssue]:
        """Validate JSON schema compliance."""
        issues = []
        
        # Check type field
        if 'type' not in schema:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_SCHEMA_TYPE",
                message=f"{context} must have a type field",
                details={"context": context}
            ))
        elif schema['type'] not in ['object', 'array', 'string', 'number', 'integer', 'boolean', 'null']:
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_SCHEMA_TYPE",
                message=f"{context} has invalid type: {schema['type']}",
                details={"context": context, "type": schema['type']}
            ))
            
        # For object types, check properties
        if schema.get('type') == 'object':
            if 'properties' not in schema:
                issues.append(ComplianceIssue(
                    severity="warning",
                    code="MISSING_OBJECT_PROPERTIES",
                    message=f"{context} object type should have properties",
                    details={"context": context}
                ))
            elif not isinstance(schema['properties'], dict):
                issues.append(ComplianceIssue(
                    severity="error",
                    code="INVALID_PROPERTIES_TYPE",
                    message=f"{context} properties must be a dictionary",
                    details={"context": context}
                ))
                
        return issues
        
    def validate_call_tool_request(self, request: CallToolRequest) -> List[ComplianceIssue]:
        """Validate MCP call tool request compliance."""
        issues = []
        
        # Check tool name
        if not hasattr(request, 'name') or not request.name:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_TOOL_NAME_IN_CALL",
                message="Call tool request must include tool name"
            ))
        elif not isinstance(request.name, str):
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_TOOL_NAME_TYPE_IN_CALL",
                message="Tool name must be a string"
            ))
            
        # Check arguments
        if hasattr(request, 'arguments') and request.arguments is not None:
            if not isinstance(request.arguments, dict):
                issues.append(ComplianceIssue(
                    severity="error",
                    code="INVALID_ARGUMENTS_TYPE",
                    message="Tool arguments must be a dictionary"
                ))
                
        return issues
        
    def validate_call_tool_result(self, result: CallToolResult) -> List[ComplianceIssue]:
        """Validate MCP call tool result compliance."""
        issues = []
        
        # Check content array
        if not hasattr(result, 'content') or result.content is None:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_RESULT_CONTENT",
                message="Call tool result must include content"
            ))
        elif not isinstance(result.content, list):
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_CONTENT_TYPE",
                message="Result content must be an array"
            ))
        else:
            # Validate each content item
            for i, content_item in enumerate(result.content):
                content_issues = self.validate_content_item(content_item, f"content[{i}]")
                issues.extend(content_issues)
                
        # Check if result indicates an error
        if hasattr(result, 'isError') and result.isError:
            # Error results should have appropriate content
            if result.content and len(result.content) > 0:
                first_content = result.content[0]
                if hasattr(first_content, 'type') and first_content.type == 'text':
                    # Try to parse error information
                    try:
                        import json
                        error_data = json.loads(first_content.text)
                        if not isinstance(error_data, dict) or 'error_code' not in error_data:
                            issues.append(ComplianceIssue(
                                severity="warning",
                                code="UNSTRUCTURED_ERROR_RESPONSE",
                                message="Error responses should include structured error information"
                            ))
                    except (json.JSONDecodeError, AttributeError):
                        issues.append(ComplianceIssue(
                            severity="warning",
                            code="UNPARSEABLE_ERROR_RESPONSE",
                            message="Error response content is not valid JSON"
                        ))
                        
        return issues
        
    def validate_content_item(self, content: Any, context: str) -> List[ComplianceIssue]:
        """Validate MCP content item compliance."""
        issues = []
        
        # Check type field
        if not hasattr(content, 'type') or not content.type:
            issues.append(ComplianceIssue(
                severity="error",
                code="MISSING_CONTENT_TYPE",
                message=f"{context} must have a type field",
                details={"context": context}
            ))
        elif content.type not in ['text', 'image', 'resource']:
            issues.append(ComplianceIssue(
                severity="error",
                code="INVALID_CONTENT_TYPE_VALUE",
                message=f"{context} has invalid type: {content.type}",
                details={"context": context, "type": content.type}
            ))
            
        # Validate based on content type
        if hasattr(content, 'type'):
            if content.type == 'text':
                if not hasattr(content, 'text') or content.text is None:
                    issues.append(ComplianceIssue(
                        severity="error",
                        code="MISSING_TEXT_CONTENT",
                        message=f"{context} text content must have text field",
                        details={"context": context}
                    ))
                elif not isinstance(content.text, str):
                    issues.append(ComplianceIssue(
                        severity="error",
                        code="INVALID_TEXT_CONTENT_TYPE",
                        message=f"{context} text field must be a string",
                        details={"context": context}
                    ))
                    
        return issues
        
    def get_compliance_report(self, 
                            initialize_request: Optional[InitializeRequest] = None,
                            initialize_result: Optional[InitializeResult] = None,
                            tools_result: Optional[ListToolsResult] = None) -> Dict[str, Any]:
        """Generate comprehensive compliance report."""
        report = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "overall_status": "compliant",
            "issues": [],
            "summary": {
                "total_issues": 0,
                "errors": 0,
                "warnings": 0,
                "info": 0
            }
        }
        
        all_issues = []
        
        # Validate initialize request/result
        if initialize_request:
            issues = self.validate_initialize_request(initialize_request)
            all_issues.extend(issues)
            
        if initialize_result and initialize_request:
            issues = self.validate_initialize_result(initialize_result, initialize_request.protocolVersion)
            all_issues.extend(issues)
            
        # Validate tools
        if tools_result:
            issues = self.validate_list_tools_result(tools_result)
            all_issues.extend(issues)
            
        # Compile report
        report["issues"] = [
            {
                "severity": issue.severity,
                "code": issue.code,
                "message": issue.message,
                "details": issue.details
            }
            for issue in all_issues
        ]
        
        # Calculate summary
        severity_to_summary_key = {
            "error": "errors",
            "warning": "warnings",
            "info": "info"
        }
        for issue in all_issues:
            report["summary"]["total_issues"] += 1
            summary_key = severity_to_summary_key.get(issue.severity, issue.severity)
            report["summary"][summary_key] = report["summary"].get(summary_key, 0) + 1
            
        # Determine overall status
        if report["summary"]["errors"] > 0:
            report["overall_status"] = "non_compliant"
        elif report["summary"]["warnings"] > 0:
            report["overall_status"] = "compliant_with_warnings"
            
        return report
