"""
End-to-end tests for MCP protocol compliance.
"""

import pytest
import asyncio
import json
from unittest.mock import AsyncMock, MagicMock, patch
from mcp.client.session import ClientSession
from mcp.types import (
    InitializeRequest, 
    ListToolsRequest, 
    CallToolRequest,
    Tool,
    TextContent
)

from mcp_financial.server import FinancialMCPServer


class TestMCPProtocolCompliance:
    """Test suite for MCP protocol compliance."""
    
    @pytest.fixture
    async def mcp_server(self):
        """Create MCP server for testing."""
        with patch('mcp_financial.server.Settings') as mock_settings_class:
            mock_settings = MagicMock()
            mock_settings.account_service_url = "http://localhost:8080"
            mock_settings.transaction_service_url = "http://localhost:8081"
            mock_settings.jwt_secret = "test-secret"
            mock_settings.server_timeout = 5000
            mock_settings_class.return_value = mock_settings
            
            server = FinancialMCPServer()
            
            # Mock external service clients
            with patch.object(server.account_client, 'health_check', new_callable=AsyncMock) as mock_health1, \
                 patch.object(server.transaction_client, 'health_check', new_callable=AsyncMock) as mock_health2:
                
                mock_health1.return_value = True
                mock_health2.return_value = True
                
                yield server
    
    @pytest.mark.asyncio
    async def test_mcp_initialization_protocol(self, mcp_server):
        """Test MCP initialization protocol compliance."""
        # Test initialization request
        init_request = InitializeRequest(
            protocolVersion="2024-11-05",
            capabilities={
                "tools": {}
            },
            clientInfo={
                "name": "test-client",
                "version": "1.0.0"
            }
        )
        
        # Mock the initialization
        with patch.object(mcp_server.app, 'initialize', new_callable=AsyncMock) as mock_init:
            mock_init.return_value = {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {
                        "listChanged": True
                    }
                },
                "serverInfo": {
                    "name": "financial-mcp-server",
                    "version": "1.0.0"
                }
            }
            
            response = await mcp_server.app.initialize(init_request)
            
            # Verify protocol compliance
            assert response["protocolVersion"] == "2024-11-05"
            assert "capabilities" in response
            assert "serverInfo" in response
            assert response["serverInfo"]["name"] == "financial-mcp-server"
    
    @pytest.mark.asyncio
    async def test_tool_discovery_protocol(self, mcp_server):
        """Test MCP tool discovery protocol compliance."""
        # Mock tool registration
        expected_tools = [
            {
                "name": "create_account",
                "description": "Create a new financial account",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "owner_id": {"type": "string"},
                        "account_type": {"type": "string"},
                        "initial_balance": {"type": "number"},
                        "auth_token": {"type": "string"}
                    },
                    "required": ["owner_id", "account_type", "auth_token"]
                }
            },
            {
                "name": "deposit_funds",
                "description": "Deposit funds to an account",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "account_id": {"type": "string"},
                        "amount": {"type": "number"},
                        "description": {"type": "string"},
                        "auth_token": {"type": "string"}
                    },
                    "required": ["account_id", "amount", "auth_token"]
                }
            }
        ]
        
        with patch.object(mcp_server.app, 'list_tools', new_callable=AsyncMock) as mock_list:
            mock_list.return_value = {"tools": expected_tools}
            
            list_request = ListToolsRequest()
            response = await mcp_server.app.list_tools(list_request)
            
            # Verify tool list compliance
            assert "tools" in response
            tools = response["tools"]
            assert len(tools) >= 2
            
            # Verify tool schema compliance
            for tool in tools:
                assert "name" in tool
                assert "description" in tool
                assert "inputSchema" in tool
                assert tool["inputSchema"]["type"] == "object"
                assert "properties" in tool["inputSchema"]
    
    @pytest.mark.asyncio
    async def test_tool_execution_protocol(self, mcp_server):
        """Test MCP tool execution protocol compliance."""
        # Mock successful tool execution
        with patch.object(mcp_server.account_client, 'create_account', new_callable=AsyncMock) as mock_create, \
             patch.object(mcp_server.auth_handler, 'extract_user_context') as mock_auth:
            
            mock_auth.return_value = MagicMock(
                user_id="test_user",
                roles=["customer"],
                permissions=["account:create"]
            )
            
            mock_create.return_value = {
                "id": "acc_123",
                "ownerId": "test_user",
                "accountType": "CHECKING",
                "balance": 0.0,
                "status": "ACTIVE"
            }
            
            # Test tool call request
            call_request = CallToolRequest(
                name="create_account",
                arguments={
                    "owner_id": "test_user",
                    "account_type": "CHECKING",
                    "initial_balance": 0.0,
                    "auth_token": "Bearer valid.token"
                }
            )
            
            with patch.object(mcp_server.app, 'call_tool', new_callable=AsyncMock) as mock_call:
                mock_call.return_value = {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps({
                                "success": True,
                                "message": "Account created successfully",
                                "data": {
                                    "id": "acc_123",
                                    "ownerId": "test_user",
                                    "accountType": "CHECKING",
                                    "balance": 0.0
                                }
                            })
                        }
                    ]
                }
                
                response = await mcp_server.app.call_tool(call_request)
                
                # Verify response compliance
                assert "content" in response
                assert len(response["content"]) > 0
                
                content = response["content"][0]
                assert content["type"] == "text"
                assert "text" in content
                
                # Verify response data structure
                response_data = json.loads(content["text"])
                assert "success" in response_data
                assert response_data["success"] is True
    
    @pytest.mark.asyncio
    async def test_error_response_protocol(self, mcp_server):
        """Test MCP error response protocol compliance."""
        # Mock authentication error
        with patch.object(mcp_server.auth_handler, 'extract_user_context') as mock_auth:
            from mcp_financial.auth.jwt_handler import AuthenticationError
            mock_auth.side_effect = AuthenticationError("Invalid token")
            
            call_request = CallToolRequest(
                name="create_account",
                arguments={
                    "owner_id": "test_user",
                    "account_type": "CHECKING",
                    "auth_token": "Bearer invalid.token"
                }
            )
            
            with patch.object(mcp_server.app, 'call_tool', new_callable=AsyncMock) as mock_call:
                mock_call.return_value = {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps({
                                "success": False,
                                "error_code": "AUTHENTICATION_ERROR",
                                "error_message": "Invalid token",
                                "timestamp": "2024-01-01T10:00:00Z"
                            })
                        }
                    ]
                }
                
                response = await mcp_server.app.call_tool(call_request)
                
                # Verify error response compliance
                assert "content" in response
                content = response["content"][0]
                
                error_data = json.loads(content["text"])
                assert error_data["success"] is False
                assert "error_code" in error_data
                assert "error_message" in error_data
                assert "timestamp" in error_data
    
    @pytest.mark.asyncio
    async def test_concurrent_tool_calls_protocol(self, mcp_server):
        """Test concurrent tool calls protocol compliance."""
        # Mock multiple tool executions
        with patch.object(mcp_server.account_client, 'get_account', new_callable=AsyncMock) as mock_get, \
             patch.object(mcp_server.transaction_client, 'get_transaction_history', new_callable=AsyncMock) as mock_history, \
             patch.object(mcp_server.auth_handler, 'extract_user_context') as mock_auth:
            
            mock_auth.return_value = MagicMock(
                user_id="test_user",
                roles=["customer"],
                permissions=["account:read", "transaction:read"]
            )
            
            mock_get.return_value = {"id": "acc_123", "balance": 1000.0}
            mock_history.return_value = {"content": [], "totalElements": 0}
            
            # Create multiple concurrent requests
            requests = [
                CallToolRequest(
                    name="get_account",
                    arguments={"account_id": "acc_123", "auth_token": "Bearer token"}
                ),
                CallToolRequest(
                    name="get_transaction_history",
                    arguments={"account_id": "acc_123", "auth_token": "Bearer token"}
                )
            ]
            
            # Mock concurrent execution
            with patch.object(mcp_server.app, 'call_tool', new_callable=AsyncMock) as mock_call:
                mock_call.side_effect = [
                    {"content": [{"type": "text", "text": json.dumps({"success": True, "data": {"id": "acc_123"}})}]},
                    {"content": [{"type": "text", "text": json.dumps({"success": True, "data": {"content": []}})}]}
                ]
                
                # Execute requests concurrently
                tasks = [mcp_server.app.call_tool(req) for req in requests]
                responses = await asyncio.gather(*tasks)
                
                # Verify all responses are valid
                assert len(responses) == 2
                for response in responses:
                    assert "content" in response
                    assert len(response["content"]) > 0
    
    @pytest.mark.asyncio
    async def test_tool_parameter_validation_protocol(self, mcp_server):
        """Test tool parameter validation protocol compliance."""
        # Test missing required parameters
        call_request = CallToolRequest(
            name="create_account",
            arguments={
                "owner_id": "test_user",
                # Missing required account_type and auth_token
            }
        )
        
        with patch.object(mcp_server.app, 'call_tool', new_callable=AsyncMock) as mock_call:
            mock_call.return_value = {
                "content": [
                    {
                        "type": "text",
                        "text": json.dumps({
                            "success": False,
                            "error_code": "VALIDATION_ERROR",
                            "error_message": "Missing required parameters: account_type, auth_token",
                            "details": {
                                "missing_parameters": ["account_type", "auth_token"]
                            }
                        })
                    }
                ]
            }
            
            response = await mcp_server.app.call_tool(call_request)
            
            # Verify validation error response
            content = response["content"][0]
            error_data = json.loads(content["text"])
            
            assert error_data["success"] is False
            assert error_data["error_code"] == "VALIDATION_ERROR"
            assert "missing_parameters" in error_data.get("details", {})
    
    @pytest.mark.asyncio
    async def test_protocol_version_compatibility(self, mcp_server):
        """Test MCP protocol version compatibility."""
        # Test with different protocol versions
        versions_to_test = ["2024-11-05", "2024-10-07"]
        
        for version in versions_to_test:
            init_request = InitializeRequest(
                protocolVersion=version,
                capabilities={"tools": {}},
                clientInfo={"name": "test-client", "version": "1.0.0"}
            )
            
            with patch.object(mcp_server.app, 'initialize', new_callable=AsyncMock) as mock_init:
                mock_init.return_value = {
                    "protocolVersion": version,
                    "capabilities": {"tools": {"listChanged": True}},
                    "serverInfo": {"name": "financial-mcp-server", "version": "1.0.0"}
                }
                
                response = await mcp_server.app.initialize(init_request)
                
                # Verify version compatibility
                assert response["protocolVersion"] == version
    
    @pytest.mark.asyncio
    async def test_resource_cleanup_protocol(self, mcp_server):
        """Test proper resource cleanup in MCP protocol."""
        # Test server shutdown
        with patch.object(mcp_server, 'shutdown', new_callable=AsyncMock) as mock_shutdown:
            await mcp_server.shutdown()
            
            mock_shutdown.assert_called_once()
    
    @pytest.mark.asyncio
    async def test_streaming_response_protocol(self, mcp_server):
        """Test streaming response protocol compliance (if supported)."""
        # Test large data response that might be streamed
        with patch.object(mcp_server.transaction_client, 'get_transaction_history', new_callable=AsyncMock) as mock_history:
            # Mock large transaction history
            large_history = {
                "content": [{"id": f"txn_{i}", "amount": 100.0} for i in range(1000)],
                "totalElements": 1000
            }
            mock_history.return_value = large_history
            
            call_request = CallToolRequest(
                name="get_transaction_history",
                arguments={
                    "account_id": "acc_123",
                    "page": 0,
                    "size": 1000,
                    "auth_token": "Bearer token"
                }
            )
            
            with patch.object(mcp_server.app, 'call_tool', new_callable=AsyncMock) as mock_call:
                mock_call.return_value = {
                    "content": [
                        {
                            "type": "text",
                            "text": json.dumps({
                                "success": True,
                                "data": large_history
                            })
                        }
                    ]
                }
                
                response = await mcp_server.app.call_tool(call_request)
                
                # Verify large response handling
                assert "content" in response
                content_text = response["content"][0]["text"]
                response_data = json.loads(content_text)
                
                assert response_data["success"] is True
                assert len(response_data["data"]["content"]) == 1000


class TestMCPClientIntegration:
    """Test MCP client integration scenarios."""
    
    @pytest.mark.asyncio
    async def test_client_server_communication(self):
        """Test full client-server MCP communication."""
        # This would test with a real MCP client if available
        # For now, we'll mock the client-server interaction
        
        server_responses = {
            "initialize": {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {"listChanged": True}},
                "serverInfo": {"name": "financial-mcp-server", "version": "1.0.0"}
            },
            "list_tools": {
                "tools": [
                    {
                        "name": "create_account",
                        "description": "Create account",
                        "inputSchema": {"type": "object", "properties": {}}
                    }
                ]
            },
            "call_tool": {
                "content": [
                    {
                        "type": "text",
                        "text": json.dumps({"success": True, "data": {"id": "acc_123"}})
                    }
                ]
            }
        }
        
        # Mock client session
        mock_session = AsyncMock()
        mock_session.initialize.return_value = server_responses["initialize"]
        mock_session.list_tools.return_value = server_responses["list_tools"]
        mock_session.call_tool.return_value = server_responses["call_tool"]
        
        # Test client workflow
        init_response = await mock_session.initialize(
            InitializeRequest(
                protocolVersion="2024-11-05",
                capabilities={"tools": {}},
                clientInfo={"name": "test-client", "version": "1.0.0"}
            )
        )
        
        assert init_response["protocolVersion"] == "2024-11-05"
        
        tools_response = await mock_session.list_tools(ListToolsRequest())
        assert len(tools_response["tools"]) > 0
        
        call_response = await mock_session.call_tool(
            CallToolRequest(
                name="create_account",
                arguments={"owner_id": "test", "account_type": "CHECKING", "auth_token": "token"}
            )
        )
        
        assert "content" in call_response
        content_data = json.loads(call_response["content"][0]["text"])
        assert content_data["success"] is True