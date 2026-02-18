# MCP Financial Integration Examples

This document provides comprehensive examples for integrating with the Financial MCP Server.

## Table of Contents

- [Python Client Example](#python-client-example)
- [JavaScript/Node.js Example](#javascriptnodejs-example)
- [Kiro IDE Integration](#kiro-ide-integration)
- [Custom Plugin Development](#custom-plugin-development)
- [Error Handling Patterns](#error-handling-patterns)

## Python Client Example

### Basic MCP Client

```python
import asyncio
import json
from mcp.client.session import ClientSession
from mcp.client.stdio import StdioServerParameters
from mcp.types import CallToolRequest, InitializeRequest

class FinancialMCPClient:
    def __init__(self, server_command: list):
        self.server_params = StdioServerParameters(
            command=server_command[0],
            args=server_command[1:] if len(server_command) > 1 else []
        )
        self.session = None
        
    async def connect(self):
        """Connect to the MCP server."""
        self.session = await ClientSession.create(self.server_params)
        
        # Initialize the session
        init_result = await self.session.initialize(
            InitializeRequest(
                protocolVersion="2024-11-05",
                capabilities={"tools": {}},
                clientInfo={"name": "financial-client", "version": "1.0.0"}
            )
        )
        
        print(f"Connected to server: {init_result.serverInfo}")
        return init_result
        
    async def list_available_tools(self):
        """List all available financial tools."""
        tools_result = await self.session.list_tools()
        return tools_result.tools
        
    async def create_account(self, owner_id: str, account_type: str, 
                           initial_balance: float, auth_token: str):
        """Create a new financial account."""
        request = CallToolRequest(
            name="create_account",
            arguments={
                "owner_id": owner_id,
                "account_type": account_type,
                "initial_balance": initial_balance,
                "auth_token": auth_token
            }
        )
        
        result = await self.session.call_tool(request)
        return self._parse_response(result)
        
    async def deposit_funds(self, account_id: str, amount: float, 
                          description: str, auth_token: str):
        """Deposit funds to an account."""
        request = CallToolRequest(
            name="deposit_funds",
            arguments={
                "account_id": account_id,
                "amount": amount,
                "description": description,
                "auth_token": auth_token
            }
        )
        
        result = await self.session.call_tool(request)
        return self._parse_response(result)
        
    async def get_transaction_history(self, account_id: str, auth_token: str,
                                    page: int = 0, size: int = 20):
        """Get transaction history for an account."""
        request = CallToolRequest(
            name="get_transaction_history",
            arguments={
                "account_id": account_id,
                "page": page,
                "size": size,
                "auth_token": auth_token
            }
        )
        
        result = await self.session.call_tool(request)
        return self._parse_response(result)
        
    def _parse_response(self, result):
        """Parse MCP tool response."""
        if result.content and len(result.content) > 0:
            content = result.content[0]
            if content.type == "text":
                return json.loads(content.text)
        return None
        
    async def close(self):
        """Close the connection."""
        if self.session:
            await self.session.close()

# Usage example
async def main():
    client = FinancialMCPClient(["python", "-m", "mcp_financial"])
    
    try:
        # Connect to server
        await client.connect()
        
        # List available tools
        tools = await client.list_available_tools()
        print(f"Available tools: {[tool.name for tool in tools]}")
        
        # Create an account
        auth_token = "Bearer your-jwt-token-here"
        account_result = await client.create_account(
            owner_id="user123",
            account_type="CHECKING",
            initial_balance=1000.0,
            auth_token=auth_token
        )
        
        if account_result and account_result.get("success"):
            account_id = account_result["data"]["id"]
            print(f"Created account: {account_id}")
            
            # Make a deposit
            deposit_result = await client.deposit_funds(
                account_id=account_id,
                amount=500.0,
                description="Initial deposit",
                auth_token=auth_token
            )
            
            if deposit_result and deposit_result.get("success"):
                print("Deposit successful")
                
                # Get transaction history
                history = await client.get_transaction_history(
                    account_id=account_id,
                    auth_token=auth_token
                )
                
                if history and history.get("success"):
                    transactions = history["data"]["content"]
                    print(f"Found {len(transactions)} transactions")
                    
    finally:
        await client.close()

if __name__ == "__main__":
    asyncio.run(main())
```

## JavaScript/Node.js Example

### MCP Client with Node.js

```javascript
const { Client } = require('@modelcontextprotocol/sdk/client/index.js');
const { StdioClientTransport } = require('@modelcontextprotocol/sdk/client/stdio.js');

class FinancialMCPClient {
    constructor(serverCommand) {
        this.serverCommand = serverCommand;
        this.client = null;
    }
    
    async connect() {
        const transport = new StdioClientTransport({
            command: this.serverCommand[0],
            args: this.serverCommand.slice(1)
        });
        
        this.client = new Client({
            name: "financial-client-js",
            version: "1.0.0"
        }, {
            capabilities: {
                tools: {}
            }
        });
        
        await this.client.connect(transport);
        
        console.log("Connected to Financial MCP Server");
        return this.client;
    }
    
    async listTools() {
        const result = await this.client.listTools();
        return result.tools;
    }
    
    async createAccount(ownerId, accountType, initialBalance, authToken) {
        const result = await this.client.callTool({
            name: "create_account",
            arguments: {
                owner_id: ownerId,
                account_type: accountType,
                initial_balance: initialBalance,
                auth_token: authToken
            }
        });
        
        return this.parseResponse(result);
    }
    
    async transferFunds(fromAccountId, toAccountId, amount, description, authToken) {
        const result = await this.client.callTool({
            name: "transfer_funds",
            arguments: {
                from_account_id: fromAccountId,
                to_account_id: toAccountId,
                amount: amount,
                description: description,
                auth_token: authToken
            }
        });
        
        return this.parseResponse(result);
    }
    
    async searchTransactions(filters, authToken) {
        const result = await this.client.callTool({
            name: "search_transactions",
            arguments: {
                ...filters,
                auth_token: authToken
            }
        });
        
        return this.parseResponse(result);
    }
    
    parseResponse(result) {
        if (result.content && result.content.length > 0) {
            const content = result.content[0];
            if (content.type === "text") {
                return JSON.parse(content.text);
            }
        }
        return null;
    }
    
    async close() {
        if (this.client) {
            await this.client.close();
        }
    }
}

// Usage example
async function main() {
    const client = new FinancialMCPClient(["python", "-m", "mcp_financial"]);
    
    try {
        await client.connect();
        
        const authToken = "Bearer your-jwt-token-here";
        
        // Create two accounts
        const account1 = await client.createAccount(
            "user1", "CHECKING", 2000.0, authToken
        );
        
        const account2 = await client.createAccount(
            "user2", "SAVINGS", 1000.0, authToken
        );
        
        if (account1.success && account2.success) {
            console.log(`Created accounts: ${account1.data.id}, ${account2.data.id}`);
            
            // Transfer funds between accounts
            const transfer = await client.transferFunds(
                account1.data.id,
                account2.data.id,
                300.0,
                "Monthly transfer",
                authToken
            );
            
            if (transfer.success) {
                console.log("Transfer completed successfully");
                
                // Search for transfer transactions
                const searchResults = await client.searchTransactions({
                    transaction_type: "TRANSFER",
                    min_amount: 250.0,
                    page: 0,
                    size: 10
                }, authToken);
                
                if (searchResults.success) {
                    console.log(`Found ${searchResults.data.content.length} transfer transactions`);
                }
            }
        }
        
    } catch (error) {
        console.error("Error:", error);
    } finally {
        await client.close();
    }
}

main().catch(console.error);
```

## Kiro IDE Integration

### MCP Configuration for Kiro

Create `.kiro/settings/mcp.json`:

```json
{
  "mcpServers": {
    "financial-services": {
      "command": "python",
      "args": ["-m", "mcp_financial"],
      "env": {
        "ACCOUNT_SERVICE_URL": "http://localhost:8080",
        "TRANSACTION_SERVICE_URL": "http://localhost:8081",
        "JWT_SECRET": "your-jwt-secret"
      },
      "disabled": false,
      "autoApprove": [
        "health_check",
        "get_service_status",
        "get_account",
        "get_transaction_history"
      ]
    }
  }
}
```

### Kiro Chat Integration Example

```markdown
# Financial Operations with MCP

## Create Account
@financial-services create_account owner_id="user123" account_type="CHECKING" initial_balance=1500.0 auth_token="Bearer eyJ..."

## Check Account Balance
@financial-services get_account account_id="acc_123456" auth_token="Bearer eyJ..."

## Make Deposit
@financial-services deposit_funds account_id="acc_123456" amount=250.0 description="Bonus payment" auth_token="Bearer eyJ..."

## View Recent Transactions
@financial-services get_transaction_history account_id="acc_123456" page=0 size=5 auth_token="Bearer eyJ..."
```

## Custom Plugin Development

### Creating a Custom Financial Plugin

```python
# custom_analytics_plugin.py
import logging
from typing import Dict, Any, List
from mcp.server.fastmcp import FastMCP
from mcp.types import Tool, TextContent

from mcp_financial.plugins.base_plugin import FinancialToolPlugin, ToolMetadata

logger = logging.getLogger(__name__)

class AnalyticsPlugin(FinancialToolPlugin):
    """Custom plugin for financial analytics tools."""
    
    def __init__(self):
        super().__init__("analytics", "1.0.0")
        
    def get_tools(self) -> Dict[str, ToolMetadata]:
        """Get all analytics tools."""
        return {
            "calculate_monthly_spending": ToolMetadata(
                name="calculate_monthly_spending",
                description="Calculate monthly spending patterns",
                version="1.0.0",
                author="Analytics Team",
                category="analytics",
                tags=["spending", "analysis", "monthly"],
                requires_auth=True,
                permissions=["transaction:read", "account:read"]
            ),
            "generate_spending_report": ToolMetadata(
                name="generate_spending_report",
                description="Generate comprehensive spending report",
                version="1.0.0",
                author="Analytics Team", 
                category="analytics",
                tags=["report", "spending", "analysis"],
                requires_auth=True,
                permissions=["transaction:read", "account:read"]
            )
        }
        
    async def register_tools(self, app: FastMCP, context: Dict[str, Any]) -> None:
        """Register analytics tools with the MCP application."""
        
        @app.tool()
        async def calculate_monthly_spending(
            account_id: str,
            year: int,
            month: int,
            auth_token: str
        ) -> List[TextContent]:
            """Calculate monthly spending for an account."""
            try:
                # Validate authentication
                user_context = self.validate_auth_context(auth_token)
                
                # Check permissions
                if not self.check_permissions(user_context, ["transaction:read"]):
                    return [TextContent(
                        type="text",
                        text='{"success": false, "error_code": "INSUFFICIENT_PERMISSIONS", "error_message": "Missing required permissions"}'
                    )]
                
                # Get transaction history for the month
                start_date = f"{year}-{month:02d}-01T00:00:00Z"
                if month == 12:
                    end_date = f"{year + 1}-01-01T00:00:00Z"
                else:
                    end_date = f"{year}-{month + 1:02d}-01T00:00:00Z"
                
                # Call transaction service to get monthly transactions
                transactions = await self.transaction_client.get_transaction_history(
                    account_id=account_id,
                    start_date=start_date,
                    end_date=end_date,
                    auth_token=auth_token
                )
                
                # Calculate spending by category
                spending_by_category = {}
                total_spending = 0.0
                
                for transaction in transactions.get("content", []):
                    if transaction.get("transactionType") in ["WITHDRAWAL", "TRANSFER"]:
                        amount = abs(float(transaction.get("amount", 0)))
                        category = transaction.get("category", "Other")
                        
                        spending_by_category[category] = spending_by_category.get(category, 0) + amount
                        total_spending += amount
                
                result = {
                    "success": True,
                    "data": {
                        "account_id": account_id,
                        "period": f"{year}-{month:02d}",
                        "total_spending": total_spending,
                        "spending_by_category": spending_by_category,
                        "transaction_count": len(transactions.get("content", []))
                    }
                }
                
                return [TextContent(type="text", text=str(result))]
                
            except Exception as e:
                logger.error(f"Error calculating monthly spending: {e}")
                return [TextContent(
                    type="text",
                    text=f'{{"success": false, "error_code": "CALCULATION_ERROR", "error_message": "{str(e)}"}}'
                )]
                
        @app.tool()
        async def generate_spending_report(
            account_id: str,
            start_date: str,
            end_date: str,
            auth_token: str
        ) -> List[TextContent]:
            """Generate comprehensive spending report."""
            try:
                # Validate authentication
                user_context = self.validate_auth_context(auth_token)
                
                # Check permissions
                if not self.check_permissions(user_context, ["transaction:read", "account:read"]):
                    return [TextContent(
                        type="text",
                        text='{"success": false, "error_code": "INSUFFICIENT_PERMISSIONS", "error_message": "Missing required permissions"}'
                    )]
                
                # Get account information
                account = await self.account_client.get_account(account_id, auth_token)
                
                # Get transaction history
                transactions = await self.transaction_client.get_transaction_history(
                    account_id=account_id,
                    start_date=start_date,
                    end_date=end_date,
                    auth_token=auth_token
                )
                
                # Generate comprehensive report
                report = {
                    "account_info": {
                        "id": account.get("id"),
                        "type": account.get("accountType"),
                        "current_balance": account.get("balance")
                    },
                    "period": {
                        "start_date": start_date,
                        "end_date": end_date
                    },
                    "summary": {
                        "total_transactions": len(transactions.get("content", [])),
                        "total_deposits": 0.0,
                        "total_withdrawals": 0.0,
                        "net_change": 0.0
                    },
                    "categories": {},
                    "trends": {}
                }
                
                # Analyze transactions
                for transaction in transactions.get("content", []):
                    amount = float(transaction.get("amount", 0))
                    tx_type = transaction.get("transactionType")
                    
                    if tx_type == "DEPOSIT":
                        report["summary"]["total_deposits"] += amount
                    elif tx_type in ["WITHDRAWAL", "TRANSFER"]:
                        report["summary"]["total_withdrawals"] += abs(amount)
                
                report["summary"]["net_change"] = (
                    report["summary"]["total_deposits"] - 
                    report["summary"]["total_withdrawals"]
                )
                
                result = {
                    "success": True,
                    "data": report
                }
                
                return [TextContent(type="text", text=str(result))]
                
            except Exception as e:
                logger.error(f"Error generating spending report: {e}")
                return [TextContent(
                    type="text",
                    text=f'{{"success": false, "error_code": "REPORT_ERROR", "error_message": "{str(e)}"}}'
                )]
```

### Loading Custom Plugin

```python
# In your server initialization
from mcp_financial.plugins.plugin_manager import PluginManager
from pathlib import Path

# Initialize plugin manager
plugin_manager = PluginManager(app)
plugin_manager.set_context({
    'auth_handler': auth_handler,
    'account_client': account_client,
    'transaction_client': transaction_client
})

# Add plugin path
plugin_manager.add_plugin_path(Path("./custom_plugins"))

# Load specific plugin
await plugin_manager.load_plugin_from_file(Path("./custom_plugins/analytics_plugin.py"))

# Or discover and load all plugins
await plugin_manager.discover_and_load_plugins()
```

## Error Handling Patterns

### Robust Error Handling

```python
import asyncio
import logging
from typing import Optional, Dict, Any

class RobustMCPClient:
    def __init__(self, client):
        self.client = client
        self.logger = logging.getLogger(__name__)
        
    async def safe_call_tool(self, tool_name: str, arguments: Dict[str, Any], 
                           max_retries: int = 3) -> Optional[Dict[str, Any]]:
        """Safely call MCP tool with retry logic."""
        
        for attempt in range(max_retries):
            try:
                result = await self.client.call_tool({
                    "name": tool_name,
                    "arguments": arguments
                })
                
                response = self.client.parse_response(result)
                
                if response and response.get("success"):
                    return response
                elif response:
                    # Handle business logic errors
                    error_code = response.get("error_code")
                    if error_code in ["AUTHENTICATION_ERROR", "AUTHORIZATION_ERROR"]:
                        # Don't retry auth errors
                        self.logger.error(f"Authentication/Authorization error: {response}")
                        return response
                    elif error_code == "RATE_LIMIT_EXCEEDED":
                        # Wait and retry for rate limits
                        wait_time = 2 ** attempt
                        self.logger.warning(f"Rate limited, waiting {wait_time}s before retry")
                        await asyncio.sleep(wait_time)
                        continue
                        
                return response
                
            except Exception as e:
                self.logger.error(f"Attempt {attempt + 1} failed: {e}")
                if attempt == max_retries - 1:
                    raise
                    
                # Exponential backoff
                wait_time = 2 ** attempt
                await asyncio.sleep(wait_time)
                
        return None
        
    async def batch_operations(self, operations: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Execute multiple operations with proper error handling."""
        results = []
        
        for i, operation in enumerate(operations):
            try:
                result = await self.safe_call_tool(
                    operation["tool_name"],
                    operation["arguments"]
                )
                results.append({
                    "index": i,
                    "success": True,
                    "result": result
                })
                
            except Exception as e:
                self.logger.error(f"Operation {i} failed: {e}")
                results.append({
                    "index": i,
                    "success": False,
                    "error": str(e)
                })
                
        return results
```

These examples demonstrate comprehensive integration patterns for the Financial MCP Server, including client implementations, custom plugin development, and robust error handling strategies.