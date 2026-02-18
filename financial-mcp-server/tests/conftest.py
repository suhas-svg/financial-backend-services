"""
Pytest configuration and shared fixtures for MCP Financial Server tests.
"""

import pytest
import asyncio
import sys
import os
from typing import Generator
from unittest.mock import AsyncMock, MagicMock

# Add src directory to Python path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'src'))

from mcp_financial.clients.account_client import AccountServiceClient
from mcp_financial.clients.transaction_client import TransactionServiceClient
from mcp_financial.clients.base_client import BaseHTTPClient


@pytest.fixture(scope="session")
def event_loop() -> Generator[asyncio.AbstractEventLoop, None, None]:
    """Create an instance of the default event loop for the test session."""
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest.fixture
def mock_jwt_token() -> str:
    """Mock JWT token for testing."""
    return "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0X3VzZXIiLCJ1c2VybmFtZSI6InRlc3R1c2VyIiwicm9sZXMiOlsiY3VzdG9tZXIiXSwicGVybWlzc2lvbnMiOlsiYWNjb3VudDpyZWFkIl0sImlhdCI6MTcwNDEwMDAwMCwiZXhwIjoxNzA0MTAzNjAwfQ.test_signature"


@pytest.fixture
def mock_account_data() -> dict:
    """Mock account data for testing."""
    return {
        "id": "acc_test_123",
        "ownerId": "user_test_456",
        "accountType": "CHECKING",
        "balance": 1000.00,
        "status": "ACTIVE",
        "createdAt": "2024-01-01T10:00:00Z",
        "updatedAt": "2024-01-01T10:00:00Z"
    }


@pytest.fixture
def mock_transaction_data() -> dict:
    """Mock transaction data for testing."""
    return {
        "id": "txn_test_789",
        "accountId": "acc_test_123",
        "amount": 100.00,
        "transactionType": "DEPOSIT",
        "status": "COMPLETED",
        "description": "Test transaction",
        "createdAt": "2024-01-01T10:00:00Z"
    }


@pytest.fixture
def account_service_url() -> str:
    """Account service URL for testing."""
    return "http://localhost:8080"


@pytest.fixture
def transaction_service_url() -> str:
    """Transaction service URL for testing."""
    return "http://localhost:8081"


@pytest.fixture
async def account_client(account_service_url) -> AccountServiceClient:
    """Create Account Service client for testing."""
    client = AccountServiceClient(account_service_url, timeout=5000)
    yield client
    await client.close()


@pytest.fixture
async def transaction_client(transaction_service_url) -> TransactionServiceClient:
    """Create Transaction Service client for testing."""
    client = TransactionServiceClient(transaction_service_url, timeout=5000)
    yield client
    await client.close()


@pytest.fixture
async def base_http_client(account_service_url) -> BaseHTTPClient:
    """Create base HTTP client for testing."""
    client = BaseHTTPClient(account_service_url, timeout=5000)
    yield client
    await client.close()


@pytest.fixture
def mock_http_response():
    """Create a mock HTTP response."""
    def _create_response(status_code: int = 200, json_data: dict = None):
        response = MagicMock()
        response.status_code = status_code
        response.json.return_value = json_data or {"success": True}
        response.raise_for_status = MagicMock()
        
        if status_code >= 400:
            import httpx
            response.raise_for_status.side_effect = httpx.HTTPStatusError(
                f"{status_code} Error", 
                request=MagicMock(), 
                response=response
            )
        
        return response
    
    return _create_response


@pytest.fixture
def mock_paginated_response():
    """Create a mock paginated response."""
    def _create_paginated_response(content: list, total_elements: int = None, page: int = 0, size: int = 20):
        if total_elements is None:
            total_elements = len(content)
        
        total_pages = (total_elements + size - 1) // size
        
        return {
            "content": content,
            "totalElements": total_elements,
            "totalPages": total_pages,
            "number": page,
            "size": size,
            "first": page == 0,
            "last": page == total_pages - 1
        }
    
    return _create_paginated_response


# Test data fixtures
@pytest.fixture
def sample_accounts() -> list:
    """Sample account data for testing."""
    return [
        {
            "id": "acc_001",
            "ownerId": "user_001",
            "accountType": "CHECKING",
            "balance": 1500.00,
            "status": "ACTIVE"
        },
        {
            "id": "acc_002",
            "ownerId": "user_001",
            "accountType": "SAVINGS",
            "balance": 5000.00,
            "status": "ACTIVE"
        },
        {
            "id": "acc_003",
            "ownerId": "user_002",
            "accountType": "CHECKING",
            "balance": 750.00,
            "status": "ACTIVE"
        }
    ]


@pytest.fixture
def sample_transactions() -> list:
    """Sample transaction data for testing."""
    return [
        {
            "id": "txn_001",
            "accountId": "acc_001",
            "amount": 100.00,
            "transactionType": "DEPOSIT",
            "status": "COMPLETED",
            "description": "Salary deposit",
            "createdAt": "2024-01-01T09:00:00Z"
        },
        {
            "id": "txn_002",
            "accountId": "acc_001",
            "amount": -50.00,
            "transactionType": "WITHDRAWAL",
            "status": "COMPLETED",
            "description": "ATM withdrawal",
            "createdAt": "2024-01-01T14:30:00Z"
        },
        {
            "id": "txn_003",
            "accountId": "acc_002",
            "amount": 1000.00,
            "transactionType": "DEPOSIT",
            "status": "COMPLETED",
            "description": "Savings deposit",
            "createdAt": "2024-01-01T16:00:00Z"
        }
    ]


# Error simulation fixtures
@pytest.fixture
def simulate_connection_error():
    """Simulate connection error for testing."""
    import httpx
    return httpx.ConnectError("Connection failed")


@pytest.fixture
def simulate_timeout_error():
    """Simulate timeout error for testing."""
    import httpx
    return httpx.TimeoutException("Request timeout")


@pytest.fixture
def simulate_service_unavailable():
    """Simulate 503 Service Unavailable for testing."""
    def _create_503_response():
        response = MagicMock()
        response.status_code = 503
        return response
    
    return _create_503_response


# Authentication fixtures
@pytest.fixture
def admin_token() -> str:
    """Admin JWT token for testing."""
    return "Bearer admin.jwt.token.with.full.permissions"


@pytest.fixture
def financial_officer_token() -> str:
    """Financial officer JWT token for testing."""
    return "Bearer financial.officer.jwt.token"


@pytest.fixture
def customer_token() -> str:
    """Customer JWT token for testing."""
    return "Bearer customer.jwt.token.limited.permissions"


@pytest.fixture
def expired_token() -> str:
    """Expired JWT token for testing."""
    return "Bearer expired.jwt.token"


@pytest.fixture
def invalid_token() -> str:
    """Invalid JWT token for testing."""
    return "Bearer invalid.jwt.token.format"