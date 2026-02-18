"""
Unit tests for financial data query MCP tools.
"""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from datetime import datetime, timedelta
from decimal import Decimal

from mcp.types import TextContent
from mcp.server.fastmcp import FastMCP

from mcp_financial.tools.query_tools import QueryTools
from mcp_financial.auth.jwt_handler import JWTAuthHandler, UserContext
from mcp_financial.clients.account_client import AccountServiceClient
from mcp_financial.clients.transaction_client import TransactionServiceClient


@pytest.fixture
def mock_app():
    """Mock FastMCP application."""
    app = MagicMock(spec=FastMCP)
    app.tool = MagicMock(return_value=lambda func: func)  # Return the function unchanged
    return app


@pytest.fixture
def mock_account_client():
    """Mock Account Service client."""
    client = AsyncMock(spec=AccountServiceClient)
    return client


@pytest.fixture
def mock_transaction_client():
    """Mock Transaction Service client."""
    client = AsyncMock(spec=TransactionServiceClient)
    return client


@pytest.fixture
def mock_auth_handler():
    """Mock JWT authentication handler."""
    handler = AsyncMock(spec=JWTAuthHandler)
    return handler


@pytest.fixture
def query_tools(mock_app, mock_account_client, mock_transaction_client, mock_auth_handler):
    """Create QueryTools instance with mocked dependencies."""
    with patch('mcp_financial.tools.query_tools.query_tools_metrics'):
        # Create a custom decorator that captures functions
        captured_functions = {}
        
        def capture_tool():
            def decorator(func):
                captured_functions[func.__name__] = func
                return func
            return decorator
        
        mock_app.tool.side_effect = capture_tool
        
        tools = QueryTools(
            app=mock_app,
            account_client=mock_account_client,
            transaction_client=mock_transaction_client,
            auth_handler=mock_auth_handler
        )
        
        # Store references to the captured functions for testing
        tools._get_transaction_history = captured_functions.get('get_transaction_history')
        tools._search_transactions = captured_functions.get('search_transactions')
        tools._get_account_analytics = captured_functions.get('get_account_analytics')
        tools._get_transaction_limits = captured_functions.get('get_transaction_limits')
        
        return tools


@pytest.fixture
def sample_user_context():
    """Sample user context for testing."""
    return UserContext(
        user_id="user123",
        username="testuser",
        roles=["USER"],
        permissions=["READ_ACCOUNT", "READ_TRANSACTION"]
    )


@pytest.fixture
def sample_account_data():
    """Sample account data for testing."""
    return {
        "id": "acc123",
        "ownerId": "user123",
        "accountType": "CHECKING",
        "balance": 1500.00,
        "status": "ACTIVE",
        "createdAt": "2024-01-01T00:00:00Z"
    }


@pytest.fixture
def sample_transaction_history():
    """Sample transaction history data for testing."""
    return {
        "content": [
            {
                "id": "tx1",
                "accountId": "acc123",
                "transactionType": "DEPOSIT",
                "amount": 500.00,
                "status": "COMPLETED",
                "createdAt": "2024-01-15T10:00:00Z",
                "description": "Salary deposit"
            },
            {
                "id": "tx2",
                "accountId": "acc123",
                "transactionType": "WITHDRAWAL",
                "amount": 100.00,
                "status": "COMPLETED",
                "createdAt": "2024-01-14T15:30:00Z",
                "description": "ATM withdrawal"
            }
        ],
        "totalElements": 2,
        "totalPages": 1,
        "number": 0,
        "size": 20
    }


@pytest.fixture
def sample_analytics_data():
    """Sample analytics data for testing."""
    return {
        "totalTransactions": 25,
        "totalDeposits": 10,
        "totalDepositAmount": 5000.00,
        "totalWithdrawals": 8,
        "totalWithdrawalAmount": 1200.00,
        "totalTransfers": 7,
        "totalTransferAmount": 2800.00,
        "averageTransactionAmount": 320.00,
        "startingBalance": 1000.00,
        "endingBalance": 1500.00,
        "netChange": 500.00,
        "highestBalance": 1800.00,
        "lowestBalance": 800.00,
        "mostActiveDay": "Monday",
        "averageDailyTransactions": 2.5,
        "activeDays": 15,
        "trends": {
            "balanceTrend": "INCREASING",
            "volumeTrend": "STABLE",
            "spendingPattern": "MODERATE"
        }
    }


class TestQueryTools:
    """Test cases for QueryTools class."""

    def test_init(self, query_tools, mock_app):
        """Test QueryTools initialization."""
        assert query_tools.app == mock_app
        assert isinstance(query_tools.account_client, AsyncMock)
        assert isinstance(query_tools.transaction_client, AsyncMock)
        assert isinstance(query_tools.auth_handler, AsyncMock)
        
        # Verify that _register_tools was called
        mock_app.tool.assert_called()

    @pytest.mark.asyncio
    async def test_get_transaction_history_success(
        self, 
        query_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_transaction_client,
        sample_user_context,
        sample_account_data,
        sample_transaction_history
    ):
        """Test successful transaction history retrieval."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        mock_account_client.get_account.return_value = sample_account_data
        mock_transaction_client.get_transaction_history.return_value = sample_transaction_history
        
        # Get the registered tool function
        get_transaction_history_func = query_tools._get_transaction_history
        assert get_transaction_history_func is not None
        
        # Execute the tool
        result = await get_transaction_history_func(
            account_id="acc123",
            page=0,
            size=20,
            auth_token="valid_token"
        )
        
        # Verify result
        assert isinstance(result, list)
        assert len(result) == 1
        assert isinstance(result[0], TextContent)
        assert "Transaction History for Account acc123" in result[0].text
        assert "Page 1 of 1" in result[0].text
        assert "tx1" in result[0].text
        assert "DEPOSIT" in result[0].text
        assert "$500.00" in result[0].text
        
        # Verify method calls
        mock_auth_handler.extract_user_context.assert_called_once_with("valid_token")
        mock_account_client.get_account.assert_called_once_with("acc123", "valid_token")
        mock_transaction_client.get_transaction_history.assert_called_once_with(
            account_id="acc123",
            page=0,
            size=20,
            start_date=None,
            end_date=None,
            auth_token="valid_token"
        )

    @pytest.mark.asyncio
    async def test_get_transaction_history_no_auth_token(self, query_tools):
        """Test transaction history retrieval without auth token."""
        # Get the registered tool function
        get_transaction_history_func = query_tools._get_transaction_history
        assert get_transaction_history_func is not None
        
        # Execute the tool without auth token
        result = await get_transaction_history_func(
            account_id="acc123",
            auth_token=None
        )
        
        # Verify error response
        assert isinstance(result, list)
        assert len(result) == 1
        assert "Error: Authentication token is required" in result[0].text

    @pytest.mark.asyncio
    async def test_get_transaction_history_invalid_pagination(self, query_tools, mock_auth_handler, sample_user_context):
        """Test transaction history with invalid pagination parameters."""
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        
        # Get the registered tool function
        get_transaction_history_func = query_tools._get_transaction_history
        assert get_transaction_history_func is not None
        
        # Test with invalid page size
        result = await get_transaction_history_func(
            account_id="acc123",
            page=0,
            size=150,  # Exceeds max size of 100
            auth_token="valid_token"
        )
        
        # Verify error response
        assert isinstance(result, list)
        assert len(result) == 1
        assert "Pagination error" in result[0].text

    @pytest.mark.asyncio
    async def test_search_transactions_success(
        self, 
        query_tools, 
        mock_auth_handler, 
        mock_transaction_client,
        sample_user_context,
        sample_transaction_history
    ):
        """Test successful transaction search."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        mock_transaction_client.search_transactions.return_value = sample_transaction_history
        
        # Get the registered tool function
        search_transactions_func = query_tools._search_transactions
        assert search_transactions_func is not None
        
        # Execute the tool
        result = await search_transactions_func(
            account_id="acc123",
            transaction_type="DEPOSIT",
            min_amount=100.0,
            max_amount=1000.0,
            auth_token="valid_token"
        )
        
        # Verify result
        assert isinstance(result, list)
        assert len(result) == 1
        assert isinstance(result[0], TextContent)
        assert "Transaction Search Results" in result[0].text
        assert "tx1" in result[0].text
        
        # Verify search parameters were passed correctly
        expected_params = {
            "page": 0,
            "size": 20,
            "accountId": "acc123",
            "transactionType": "DEPOSIT",
            "minAmount": 100.0,
            "maxAmount": 1000.0
        }
        mock_transaction_client.search_transactions.assert_called_once_with(
            search_params=expected_params,
            auth_token="valid_token"
        )

    @pytest.mark.asyncio
    async def test_search_transactions_invalid_type(self, query_tools, mock_auth_handler, sample_user_context):
        """Test transaction search with invalid transaction type."""
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        
        # Get the registered tool function
        search_transactions_func = query_tools._search_transactions
        assert search_transactions_func is not None
        
        # Execute with invalid transaction type
        result = await search_transactions_func(
            transaction_type="INVALID_TYPE",
            auth_token="valid_token"
        )
        
        # Verify error response
        assert isinstance(result, list)
        assert len(result) == 1
        assert "transaction_type must be one of" in result[0].text

    @pytest.mark.asyncio
    async def test_get_account_analytics_success(
        self, 
        query_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_transaction_client,
        sample_user_context,
        sample_account_data,
        sample_analytics_data
    ):
        """Test successful account analytics retrieval."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        mock_account_client.get_account.return_value = sample_account_data
        mock_account_client.get_account_analytics.return_value = {}
        mock_transaction_client.get_transaction_analytics.return_value = sample_analytics_data
        
        # Get the registered tool function
        get_account_analytics_func = query_tools._get_account_analytics
        assert get_account_analytics_func is not None
        
        # Execute the tool
        result = await get_account_analytics_func(
            account_id="acc123",
            period_days=30,
            include_trends=True,
            auth_token="valid_token"
        )
        
        # Verify result
        assert isinstance(result, list)
        assert len(result) == 1
        assert isinstance(result[0], TextContent)
        assert "Account Analytics Report" in result[0].text
        assert "Account ID: acc123" in result[0].text
        assert "CHECKING" in result[0].text
        assert "$1500.00" in result[0].text
        assert "Total Transactions: 25" in result[0].text
        assert "TREND ANALYSIS" in result[0].text
        
        # Verify method calls
        mock_account_client.get_account.assert_called_once_with("acc123", "valid_token")
        mock_transaction_client.get_transaction_analytics.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_account_analytics_invalid_period(self, query_tools, mock_auth_handler, sample_user_context):
        """Test account analytics with invalid period."""
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        
        # Get the registered tool function
        get_account_analytics_func = query_tools._get_account_analytics
        assert get_account_analytics_func is not None
        
        # Test with invalid period
        result = await get_account_analytics_func(
            account_id="acc123",
            period_days=400,  # Exceeds max of 365
            auth_token="valid_token"
        )
        
        # Verify error response
        assert isinstance(result, list)
        assert len(result) == 1
        assert "period_days must be between 1 and 365" in result[0].text

    @pytest.mark.asyncio
    async def test_get_transaction_limits_success(
        self, 
        query_tools, 
        mock_auth_handler, 
        mock_account_client,
        sample_user_context,
        sample_account_data
    ):
        """Test successful transaction limits retrieval."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        mock_account_client.get_account.return_value = sample_account_data
        
        # Get the registered tool function
        get_transaction_limits_func = query_tools._get_transaction_limits
        assert get_transaction_limits_func is not None
        
        # Execute the tool
        result = await get_transaction_limits_func(
            account_id="acc123",
            auth_token="valid_token"
        )
        
        # Verify result
        assert isinstance(result, list)
        assert len(result) == 1
        assert isinstance(result[0], TextContent)
        assert "Transaction Limits Report" in result[0].text
        assert "Account ID: acc123" in result[0].text
        assert "DAILY LIMITS" in result[0].text
        assert "MONTHLY LIMITS" in result[0].text
        assert "SINGLE TRANSACTION LIMITS" in result[0].text
        assert "$1000.00" in result[0].text  # Daily withdrawal limit
        
        # Verify method calls
        mock_account_client.get_account.assert_called_once_with("acc123", "valid_token")

    @pytest.mark.asyncio
    async def test_get_transaction_limits_no_identifiers(self, query_tools, mock_auth_handler, sample_user_context):
        """Test transaction limits without account_id or user_id."""
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        
        # Get the registered tool function
        get_transaction_limits_func = query_tools._get_transaction_limits
        assert get_transaction_limits_func is not None
        
        # Execute without identifiers
        result = await get_transaction_limits_func(
            auth_token="valid_token"
        )
        
        # Verify error response
        assert isinstance(result, list)
        assert len(result) == 1
        assert "Either account_id or user_id must be provided" in result[0].text

    @pytest.mark.asyncio
    async def test_get_transaction_limits_with_user_id(
        self, 
        query_tools, 
        mock_auth_handler,
        sample_user_context
    ):
        """Test transaction limits retrieval with user_id only."""
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        
        # Get the registered tool function
        get_transaction_limits_func = query_tools._get_transaction_limits
        assert get_transaction_limits_func is not None
        
        # Execute with user_id only
        result = await get_transaction_limits_func(
            user_id="user123",
            auth_token="valid_token"
        )
        
        # Verify result
        assert isinstance(result, list)
        assert len(result) == 1
        assert isinstance(result[0], TextContent)
        assert "Transaction Limits Report" in result[0].text
        assert "User ID: user123" in result[0].text

    @pytest.mark.asyncio
    async def test_error_handling_in_tools(
        self, 
        query_tools, 
        mock_auth_handler, 
        mock_account_client,
        sample_user_context
    ):
        """Test error handling in query tools."""
        # Setup mocks to raise exceptions
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        mock_account_client.get_account.side_effect = Exception("Service unavailable")
        
        # Get the registered tool function
        get_transaction_history_func = query_tools._get_transaction_history
        assert get_transaction_history_func is not None
        
        # Execute the tool
        result = await get_transaction_history_func(
            account_id="acc123",
            auth_token="valid_token"
        )
        
        # Verify error response
        assert isinstance(result, list)
        assert len(result) == 1
        assert "Cannot access account acc123" in result[0].text
        assert "Service unavailable" in result[0].text

    @pytest.mark.asyncio
    async def test_date_validation(self, query_tools, mock_auth_handler, sample_user_context):
        """Test date format validation in query tools."""
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        
        # Get the registered tool function
        get_transaction_history_func = query_tools._get_transaction_history
        assert get_transaction_history_func is not None
        
        # Test with invalid date format
        result = await get_transaction_history_func(
            account_id="acc123",
            start_date="invalid-date",
            auth_token="valid_token"
        )
        
        # Verify error response
        assert isinstance(result, list)
        assert len(result) == 1
        assert "start_date must be in YYYY-MM-DD format" in result[0].text

    @pytest.mark.asyncio
    async def test_empty_transaction_history(
        self, 
        query_tools, 
        mock_auth_handler, 
        mock_account_client, 
        mock_transaction_client,
        sample_user_context,
        sample_account_data
    ):
        """Test transaction history with no results."""
        # Setup mocks
        mock_auth_handler.extract_user_context.return_value = sample_user_context
        mock_account_client.get_account.return_value = sample_account_data
        mock_transaction_client.get_transaction_history.return_value = {
            "content": [],
            "totalElements": 0,
            "totalPages": 0,
            "number": 0,
            "size": 20
        }
        
        # Get the registered tool function
        get_transaction_history_func = query_tools._get_transaction_history
        assert get_transaction_history_func is not None
        
        # Execute the tool
        result = await get_transaction_history_func(
            account_id="acc123",
            auth_token="valid_token"
        )
        
        # Verify result
        assert isinstance(result, list)
        assert len(result) == 1
        assert "No transactions found for the specified criteria" in result[0].text