# Financial MCP Server API Documentation

## Overview

The Financial MCP Server provides Model Context Protocol (MCP) access to financial services through a standardized interface. This documentation covers all available tools, their parameters, and usage examples.

## Table of Contents

- [Getting Started](#getting-started)
- [Authentication](#authentication)
- [Available Tools](#available-tools)
- [Error Handling](#error-handling)
- [Examples](#examples)
- [Protocol Compliance](#protocol-compliance)

## Getting Started

### Connection

Connect to the MCP server using any MCP-compatible client:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {}
    },
    "clientInfo": {
      "name": "your-client",
      "version": "1.0.0"
    }
  }
}
```

### Server Response

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2024-11-05",
    "capabilities": {
      "tools": {
        "listChanged": true
      }
    },
    "serverInfo": {
      "name": "financial-mcp-server",
      "version": "1.0.0"
    }
  }
}
```

## Authentication

All financial operations require JWT authentication. Include your JWT token in the `auth_token` parameter:

```json
{
  "auth_token": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

## Available Tools

### Account Management Tools

#### create_account

Create a new financial account.

**Parameters:**
- `owner_id` (string, required): Account owner identifier
- `account_type` (string, required): Type of account (CHECKING, SAVINGS, CREDIT)
- `initial_balance` (number, optional): Initial account balance (default: 0.0)
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "create_account",
  "arguments": {
    "owner_id": "user123",
    "account_type": "CHECKING",
    "initial_balance": 1000.0,
    "auth_token": "Bearer eyJ..."
  }
}
```

#### get_account

Retrieve account details.

**Parameters:**
- `account_id` (string, required): Account identifier
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "get_account",
  "arguments": {
    "account_id": "acc_123456",
    "auth_token": "Bearer eyJ..."
  }
}
```

#### update_account_balance

Update account balance.

**Parameters:**
- `account_id` (string, required): Account identifier
- `new_balance` (number, required): New account balance
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "update_account_balance",
  "arguments": {
    "account_id": "acc_123456",
    "new_balance": 2500.0,
    "auth_token": "Bearer eyJ..."
  }
}
```

### Transaction Processing Tools

#### deposit_funds

Deposit funds to an account.

**Parameters:**
- `account_id` (string, required): Target account identifier
- `amount` (number, required): Deposit amount (must be positive)
- `description` (string, optional): Transaction description
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "deposit_funds",
  "arguments": {
    "account_id": "acc_123456",
    "amount": 500.0,
    "description": "Salary deposit",
    "auth_token": "Bearer eyJ..."
  }
}
```

#### withdraw_funds

Withdraw funds from an account.

**Parameters:**
- `account_id` (string, required): Source account identifier
- `amount` (number, required): Withdrawal amount (must be positive)
- `description` (string, optional): Transaction description
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "withdraw_funds",
  "arguments": {
    "account_id": "acc_123456",
    "amount": 200.0,
    "description": "ATM withdrawal",
    "auth_token": "Bearer eyJ..."
  }
}
```

#### transfer_funds

Transfer funds between accounts.

**Parameters:**
- `from_account_id` (string, required): Source account identifier
- `to_account_id` (string, required): Destination account identifier
- `amount` (number, required): Transfer amount (must be positive)
- `description` (string, optional): Transaction description
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "transfer_funds",
  "arguments": {
    "from_account_id": "acc_123456",
    "to_account_id": "acc_789012",
    "amount": 300.0,
    "description": "Monthly transfer",
    "auth_token": "Bearer eyJ..."
  }
}
```

### Query Tools

#### get_transaction_history

Retrieve paginated transaction history.

**Parameters:**
- `account_id` (string, required): Account identifier
- `page` (integer, optional): Page number (default: 0)
- `size` (integer, optional): Page size (default: 20, max: 100)
- `start_date` (string, optional): Start date (ISO format)
- `end_date` (string, optional): End date (ISO format)
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "get_transaction_history",
  "arguments": {
    "account_id": "acc_123456",
    "page": 0,
    "size": 10,
    "start_date": "2024-01-01T00:00:00Z",
    "end_date": "2024-12-31T23:59:59Z",
    "auth_token": "Bearer eyJ..."
  }
}
```

#### search_transactions

Search transactions with filters.

**Parameters:**
- `account_id` (string, optional): Account identifier filter
- `transaction_type` (string, optional): Transaction type filter (DEPOSIT, WITHDRAWAL, TRANSFER)
- `min_amount` (number, optional): Minimum amount filter
- `max_amount` (number, optional): Maximum amount filter
- `description_contains` (string, optional): Description search term
- `page` (integer, optional): Page number (default: 0)
- `size` (integer, optional): Page size (default: 20)
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "search_transactions",
  "arguments": {
    "transaction_type": "DEPOSIT",
    "min_amount": 100.0,
    "description_contains": "salary",
    "page": 0,
    "size": 20,
    "auth_token": "Bearer eyJ..."
  }
}
```

### Monitoring Tools

#### health_check

Check system health status.

**Parameters:**
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "health_check",
  "arguments": {
    "auth_token": "Bearer eyJ..."
  }
}
```

#### get_service_status

Get detailed service status information.

**Parameters:**
- `auth_token` (string, required): JWT authentication token

**Example:**
```json
{
  "name": "get_service_status",
  "arguments": {
    "auth_token": "Bearer eyJ..."
  }
}
```

## Error Handling

All tools return structured responses with consistent error handling:

### Success Response Format

```json
{
  "type": "text",
  "text": "{\"success\": true, \"data\": {...}, \"message\": \"Operation completed successfully\"}"
}
```

### Error Response Format

```json
{
  "type": "text",
  "text": "{\"success\": false, \"error_code\": \"ERROR_CODE\", \"error_message\": \"Detailed error message\", \"timestamp\": \"2024-01-01T10:00:00Z\"}"
}
```

### Common Error Codes

- `AUTHENTICATION_ERROR`: Invalid or expired JWT token
- `AUTHORIZATION_ERROR`: Insufficient permissions
- `VALIDATION_ERROR`: Invalid parameters
- `ACCOUNT_NOT_FOUND`: Account does not exist
- `INSUFFICIENT_FUNDS`: Not enough balance for operation
- `SERVICE_UNAVAILABLE`: Backend service unavailable
- `RATE_LIMIT_EXCEEDED`: Too many requests

## Examples

### Complete Account Creation Flow

1. **Create Account**
```json
{
  "name": "create_account",
  "arguments": {
    "owner_id": "user123",
    "account_type": "CHECKING",
    "initial_balance": 1000.0,
    "auth_token": "Bearer eyJ..."
  }
}
```

2. **Response**
```json
{
  "content": [
    {
      "type": "text",
      "text": "{\"success\": true, \"data\": {\"id\": \"acc_123456\", \"ownerId\": \"user123\", \"accountType\": \"CHECKING\", \"balance\": 1000.0, \"status\": \"ACTIVE\"}, \"message\": \"Account created successfully\"}"
    }
  ]
}
```

### Transaction Processing Flow

1. **Deposit Funds**
```json
{
  "name": "deposit_funds",
  "arguments": {
    "account_id": "acc_123456",
    "amount": 500.0,
    "description": "Salary deposit",
    "auth_token": "Bearer eyJ..."
  }
}
```

2. **Check Balance**
```json
{
  "name": "get_account",
  "arguments": {
    "account_id": "acc_123456",
    "auth_token": "Bearer eyJ..."
  }
}
```

3. **View Transaction History**
```json
{
  "name": "get_transaction_history",
  "arguments": {
    "account_id": "acc_123456",
    "page": 0,
    "size": 10,
    "auth_token": "Bearer eyJ..."
  }
}
```

## Protocol Compliance

This server implements MCP Protocol version 2024-11-05 with full compliance including:

- Complete initialization handshake
- Tool discovery and metadata
- Structured tool execution
- Error handling and reporting
- Backward compatibility with 2024-10-07

### Supported Protocol Features

- ✅ Tools (complete implementation)
- ✅ Initialization and capabilities negotiation
- ✅ Error handling and structured responses
- ✅ Tool parameter validation
- ✅ Authentication and authorization
- ✅ Monitoring and health checks

### Version Compatibility

| Client Version | Server Support | Notes |
|---------------|----------------|-------|
| 2024-11-05    | Full          | Latest features |
| 2024-10-07    | Full          | Backward compatible |
| Earlier       | Limited       | Basic functionality only |

For more detailed information, see the [Protocol Compliance Guide](protocol-compliance.md).