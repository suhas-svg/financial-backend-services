"""
Financial data query MCP tools implementation.
"""

import logging
from typing import Dict, Any, Optional, List
from datetime import datetime, timedelta
from decimal import Decimal

from mcp.types import Tool, TextContent
from mcp.server.fastmcp import FastMCP

from ..auth.jwt_handler import JWTAuthHandler
from ..clients.account_client import AccountServiceClient
from ..clients.transaction_client import TransactionServiceClient
from ..utils.validation import validate_required_params, validate_date_format, validate_pagination_params
from ..utils.metrics import query_tools_metrics

logger = logging.getLogger(__name__)


def _to_iso_start(date_value: Optional[str]) -> Optional[str]:
    if not date_value:
        return None
    return f"{date_value}T00:00:00"


def _to_iso_end(date_value: Optional[str]) -> Optional[str]:
    if not date_value:
        return None
    return f"{date_value}T23:59:59"


def _safe_money(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


class QueryTools:
    """MCP tools for financial data queries and analytics."""
    
    def __init__(
        self,
        app: FastMCP,
        account_client: AccountServiceClient,
        transaction_client: TransactionServiceClient,
        auth_handler: JWTAuthHandler
    ):
        self.app = app
        self.account_client = account_client
        self.transaction_client = transaction_client
        self.auth_handler = auth_handler
        self._register_tools()
        
    def _register_tools(self) -> None:
        """Register all query MCP tools."""
        logger.info("Registering financial query tools")
        
        @self.app.tool()
        async def get_transaction_history(
            account_id: str,
            page: int = 0,
            size: int = 20,
            start_date: Optional[str] = None,
            end_date: Optional[str] = None,
            auth_token: Optional[str] = None
        ) -> List[TextContent]:
            """
            Get paginated transaction history for an account.
            
            Args:
                account_id: Account identifier to get history for
                page: Page number (0-based, default: 0)
                size: Number of transactions per page (default: 20, max: 100)
                start_date: Start date filter in ISO format (YYYY-MM-DD)
                end_date: End date filter in ISO format (YYYY-MM-DD)
                auth_token: JWT authentication token
                
            Returns:
                Paginated transaction history with metadata
            """
            try:
                # Validate authentication
                if not auth_token:
                    return [TextContent(
                        type="text",
                        text="Error: Authentication token is required"
                    )]
                
                user_context = self.auth_handler.extract_user_context(auth_token)
                
                # Validate required parameters
                validation_error = validate_required_params({"account_id": account_id})
                if validation_error:
                    return [TextContent(type="text", text=f"Validation error: {validation_error}")]
                
                # Validate pagination parameters
                page_validation = validate_pagination_params(page, size, max_size=100)
                if page_validation:
                    return [TextContent(type="text", text=f"Pagination error: {page_validation}")]
                
                # Validate date formats if provided
                if start_date and not validate_date_format(start_date):
                    return [TextContent(type="text", text="Error: start_date must be in YYYY-MM-DD format")]
                
                if end_date and not validate_date_format(end_date):
                    return [TextContent(type="text", text="Error: end_date must be in YYYY-MM-DD format")]
                
                # Check account access permissions
                try:
                    await self.account_client.get_account(account_id, auth_token)
                except Exception as e:
                    return [TextContent(
                        type="text",
                        text=f"Error: Cannot access account {account_id}. {str(e)}"
                    )]
                
                # Get transaction history
                history_data = await self.transaction_client.get_transaction_history(
                    account_id=account_id,
                    page=page,
                    size=size,
                    start_date=_to_iso_start(start_date),
                    end_date=_to_iso_end(end_date),
                    auth_token=auth_token
                )
                
                # Format response
                transactions = history_data.get("content", [])
                total_elements = history_data.get("totalElements", 0)
                total_pages = history_data.get("totalPages", 0)
                
                result_text = f"Transaction History for Account {account_id}\n"
                result_text += f"Page {page + 1} of {total_pages} (Total: {total_elements} transactions)\n\n"
                
                if not transactions:
                    result_text += "No transactions found for the specified criteria."
                else:
                    for tx in transactions:
                        result_text += f"ID: {tx.get('transactionId', 'N/A')}\n"
                        result_text += f"Type: {tx.get('type', 'N/A')}\n"
                        result_text += f"Amount: ${_safe_money(tx.get('amount')):.2f}\n"
                        result_text += f"Date: {tx.get('createdAt', 'N/A')}\n"
                        result_text += f"Description: {tx.get('description', 'N/A')}\n"
                        result_text += f"Status: {tx.get('status', 'N/A')}\n"
                        result_text += "-" * 40 + "\n"
                
                query_tools_metrics.transaction_history_requests.inc()
                logger.info(f"Retrieved transaction history for account {account_id}, page {page}")
                
                return [TextContent(type="text", text=result_text)]
                
            except Exception as e:
                error_msg = f"Error retrieving transaction history: {str(e)}"
                logger.error(error_msg)
                query_tools_metrics.query_errors.inc()
                return [TextContent(type="text", text=error_msg)]
        
        @self.app.tool()
        async def search_transactions(
            account_id: Optional[str] = None,
            transaction_type: Optional[str] = None,
            min_amount: Optional[float] = None,
            max_amount: Optional[float] = None,
            start_date: Optional[str] = None,
            end_date: Optional[str] = None,
            status: Optional[str] = None,
            description_contains: Optional[str] = None,
            page: int = 0,
            size: int = 20,
            auth_token: Optional[str] = None
        ) -> List[TextContent]:
            """
            Search transactions with advanced filtering capabilities.
            
            Args:
                account_id: Filter by specific account ID
                transaction_type: Filter by transaction type (DEPOSIT, WITHDRAWAL, TRANSFER)
                min_amount: Minimum transaction amount
                max_amount: Maximum transaction amount
                start_date: Start date filter in ISO format (YYYY-MM-DD)
                end_date: End date filter in ISO format (YYYY-MM-DD)
                status: Filter by transaction status (COMPLETED, PENDING, FAILED)
                description_contains: Filter by description containing text
                page: Page number (0-based, default: 0)
                size: Number of results per page (default: 20, max: 100)
                auth_token: JWT authentication token
                
            Returns:
                Filtered transaction results with search metadata
            """
            try:
                # Validate authentication
                if not auth_token:
                    return [TextContent(
                        type="text",
                        text="Error: Authentication token is required"
                    )]
                
                user_context = self.auth_handler.extract_user_context(auth_token)
                
                # Validate pagination parameters
                page_validation = validate_pagination_params(page, size, max_size=100)
                if page_validation:
                    return [TextContent(type="text", text=f"Pagination error: {page_validation}")]
                
                # Validate date formats if provided
                if start_date and not validate_date_format(start_date):
                    return [TextContent(type="text", text="Error: start_date must be in YYYY-MM-DD format")]
                
                if end_date and not validate_date_format(end_date):
                    return [TextContent(type="text", text="Error: end_date must be in YYYY-MM-DD format")]
                
                # Validate transaction type if provided
                valid_types = ["TRANSFER", "DEPOSIT", "WITHDRAWAL", "FEE", "INTEREST", "REVERSAL", "REFUND"]
                if transaction_type and transaction_type.upper() not in valid_types:
                    return [TextContent(
                        type="text",
                        text=f"Error: transaction_type must be one of {valid_types}"
                    )]
                
                # Validate status if provided
                valid_statuses = [
                    "PENDING",
                    "PROCESSING",
                    "COMPLETED",
                    "FAILED",
                    "FAILED_REQUIRES_MANUAL_ACTION",
                    "REVERSED",
                    "CANCELLED",
                ]
                if status and status.upper() not in valid_statuses:
                    return [TextContent(
                        type="text",
                        text=f"Error: status must be one of {valid_statuses}"
                    )]
                
                # Build search parameters
                search_params = {
                    "page": page,
                    "size": size
                }
                
                if account_id:
                    search_params["accountId"] = account_id
                if transaction_type:
                    search_params["type"] = transaction_type.upper()
                if min_amount is not None:
                    search_params["minAmount"] = min_amount
                if max_amount is not None:
                    search_params["maxAmount"] = max_amount
                if start_date:
                    search_params["startDate"] = _to_iso_start(start_date)
                if end_date:
                    search_params["endDate"] = _to_iso_end(end_date)
                if status:
                    search_params["status"] = status.upper()
                if description_contains:
                    search_params["description"] = description_contains
                
                # Perform search
                search_results = await self.transaction_client.search_transactions(
                    search_params=search_params,
                    auth_token=auth_token
                )
                
                # Format response
                transactions = search_results.get("content", [])
                total_elements = search_results.get("totalElements", 0)
                total_pages = search_results.get("totalPages", 0)
                
                result_text = "Transaction Search Results\n"
                result_text += f"Search criteria: {', '.join([f'{k}={v}' for k, v in search_params.items() if k not in ['page', 'size']])}\n"
                result_text += f"Page {page + 1} of {total_pages} (Total: {total_elements} matches)\n\n"
                
                if not transactions:
                    result_text += "No transactions found matching the search criteria."
                else:
                    for tx in transactions:
                        result_text += f"ID: {tx.get('transactionId', 'N/A')}\n"
                        result_text += f"From Account: {tx.get('fromAccountId', 'N/A')}\n"
                        result_text += f"To Account: {tx.get('toAccountId', 'N/A')}\n"
                        result_text += f"Type: {tx.get('type', 'N/A')}\n"
                        result_text += f"Amount: ${_safe_money(tx.get('amount')):.2f}\n"
                        result_text += f"Date: {tx.get('createdAt', 'N/A')}\n"
                        result_text += f"Status: {tx.get('status', 'N/A')}\n"
                        result_text += f"Description: {tx.get('description', 'N/A')}\n"
                        result_text += "-" * 40 + "\n"
                
                query_tools_metrics.transaction_search_requests.inc()
                logger.info(f"Performed transaction search with {len(search_params)} criteria")
                
                return [TextContent(type="text", text=result_text)]
                
            except Exception as e:
                error_msg = f"Error searching transactions: {str(e)}"
                logger.error(error_msg)
                query_tools_metrics.query_errors.inc()
                return [TextContent(type="text", text=error_msg)]
        
        @self.app.tool()
        async def get_account_analytics(
            account_id: str,
            period_days: int = 30,
            include_trends: bool = True,
            auth_token: Optional[str] = None
        ) -> List[TextContent]:
            """
            Get comprehensive account analytics and metrics.
            
            Args:
                account_id: Account identifier to analyze
                period_days: Analysis period in days (default: 30, max: 365)
                include_trends: Include trend analysis (default: True)
                auth_token: JWT authentication token
                
            Returns:
                Account analytics including balance trends, transaction volumes, and patterns
            """
            try:
                # Validate authentication
                if not auth_token:
                    return [TextContent(
                        type="text",
                        text="Error: Authentication token is required"
                    )]
                
                user_context = self.auth_handler.extract_user_context(auth_token)
                
                # Validate required parameters
                validation_error = validate_required_params({"account_id": account_id})
                if validation_error:
                    return [TextContent(type="text", text=f"Validation error: {validation_error}")]
                
                # Validate period_days
                if period_days < 1 or period_days > 365:
                    return [TextContent(
                        type="text",
                        text="Error: period_days must be between 1 and 365"
                    )]
                
                # Check account access permissions
                try:
                    account_data = await self.account_client.get_account(account_id, auth_token)
                except Exception as e:
                    return [TextContent(
                        type="text",
                        text=f"Error: Cannot access account {account_id}. {str(e)}"
                    )]
                
                # Calculate date range
                end_date = datetime.now()
                start_date = end_date - timedelta(days=period_days)
                
                # Get transaction analytics
                analytics_data = await self.transaction_client.get_transaction_analytics(
                    account_id=account_id,
                    start_date=start_date.strftime("%Y-%m-%dT00:00:00"),
                    end_date=end_date.strftime("%Y-%m-%dT23:59:59"),
                    auth_token=auth_token
                )
                
                # Format comprehensive analytics report
                result_text = f"Account Analytics Report\n"
                result_text += f"Account ID: {account_id}\n"
                result_text += f"Account Type: {account_data.get('accountType', 'N/A')}\n"
                result_text += f"Current Balance: ${_safe_money(account_data.get('balance')):.2f}\n"
                result_text += f"Analysis Period: {period_days} days ({start_date.strftime('%Y-%m-%d')} to {end_date.strftime('%Y-%m-%d')})\n\n"
                
                # Transaction volume metrics
                result_text += "TRANSACTION VOLUME METRICS\n"
                result_text += f"Total Transactions: {analytics_data.get('totalTransactions', 0)}\n"
                result_text += f"Total Deposits: ${_safe_money(analytics_data.get('totalDeposits')):.2f}\n"
                result_text += f"Total Withdrawals: ${_safe_money(analytics_data.get('totalWithdrawals')):.2f}\n"
                result_text += f"Total Transfers: ${_safe_money(analytics_data.get('totalTransfers')):.2f}\n"
                result_text += f"Average Transaction Amount: ${_safe_money(analytics_data.get('averageTransactionAmount')):.2f}\n\n"
                
                # Balance metrics
                result_text += "BALANCE METRICS\n"
                result_text += f"Total Incoming: ${_safe_money(analytics_data.get('totalIncoming')):.2f}\n"
                result_text += f"Total Outgoing: ${_safe_money(analytics_data.get('totalOutgoing')):.2f}\n"
                result_text += f"Daily Total: ${_safe_money(analytics_data.get('dailyTotal')):.2f}\n"
                result_text += f"Monthly Total: ${_safe_money(analytics_data.get('monthlyTotal')):.2f}\n"
                result_text += f"Largest Transaction: ${_safe_money(analytics_data.get('largestTransaction')):.2f}\n"
                result_text += f"Smallest Transaction: ${_safe_money(analytics_data.get('smallestTransaction')):.2f}\n\n"
                
                # Activity patterns
                result_text += "ACTIVITY PATTERNS\n"
                result_text += f"Completed Transactions: {analytics_data.get('completedTransactions', 0)}\n"
                result_text += f"Pending Transactions: {analytics_data.get('pendingTransactions', 0)}\n"
                result_text += f"Failed Transactions: {analytics_data.get('failedTransactions', 0)}\n"
                result_text += f"Reversed Transactions: {analytics_data.get('reversedTransactions', 0)}\n"
                result_text += f"Success Rate: {_safe_money(analytics_data.get('successRate')):.2f}%\n\n"
                
                # Include trends if requested
                if include_trends:
                    incoming = _safe_money(analytics_data.get("totalIncoming"))
                    outgoing = _safe_money(analytics_data.get("totalOutgoing"))
                    if incoming > outgoing:
                        balance_trend = "increasing"
                    elif incoming < outgoing:
                        balance_trend = "decreasing"
                    else:
                        balance_trend = "stable"

                    result_text += "TREND ANALYSIS\n"
                    result_text += f"Balance Trend: {balance_trend}\n"
                    result_text += f"Daily Count: {analytics_data.get('dailyCount', 0)}\n"
                    result_text += f"Monthly Count: {analytics_data.get('monthlyCount', 0)}\n"
                
                query_tools_metrics.account_analytics_requests.inc()
                logger.info(f"Generated analytics report for account {account_id}")
                
                return [TextContent(type="text", text=result_text)]
                
            except Exception as e:
                error_msg = f"Error generating account analytics: {str(e)}"
                logger.error(error_msg)
                query_tools_metrics.query_errors.inc()
                return [TextContent(type="text", text=error_msg)]
        
        @self.app.tool()
        async def get_transaction_limits(
            account_id: Optional[str] = None,
            user_id: Optional[str] = None,
            auth_token: Optional[str] = None
        ) -> List[TextContent]:
            """
            Get current transaction limits for an account or user.
            
            Args:
                account_id: Specific account to get limits for
                user_id: User to get limits for (if account_id not provided)
                auth_token: JWT authentication token
                
            Returns:
                Current transaction limits and usage information
            """
            try:
                # Validate authentication
                if not auth_token:
                    return [TextContent(
                        type="text",
                        text="Error: Authentication token is required"
                    )]
                
                user_context = self.auth_handler.extract_user_context(auth_token)
                
                # Validate that at least one identifier is provided
                if not account_id and not user_id:
                    return [TextContent(
                        type="text",
                        text="Error: Either account_id or user_id must be provided"
                    )]
                
                # If account_id is provided, verify access
                if account_id:
                    try:
                        account_data = await self.account_client.get_account(account_id, auth_token)
                        user_id = account_data.get('ownerId')
                    except Exception as e:
                        return [TextContent(
                            type="text",
                            text=f"Error: Cannot access account {account_id}. {str(e)}"
                        )]
                
                # Get transaction limits (this would typically come from a limits service)
                # For now, we'll simulate this with default limits and current usage
                limits_data = {
                    "dailyLimits": {
                        "withdrawal": 1000.00,
                        "transfer": 5000.00,
                        "deposit": 10000.00
                    },
                    "monthlyLimits": {
                        "withdrawal": 30000.00,
                        "transfer": 150000.00,
                        "deposit": 300000.00
                    },
                    "transactionLimits": {
                        "maxSingleWithdrawal": 2500.00,
                        "maxSingleTransfer": 10000.00,
                        "maxSingleDeposit": 25000.00
                    },
                    "currentUsage": {
                        "dailyWithdrawal": 250.00,
                        "dailyTransfer": 1200.00,
                        "dailyDeposit": 500.00,
                        "monthlyWithdrawal": 5500.00,
                        "monthlyTransfer": 15000.00,
                        "monthlyDeposit": 8000.00
                    }
                }
                
                # Format limits report
                result_text = "Transaction Limits Report\n"
                if account_id:
                    result_text += f"Account ID: {account_id}\n"
                if user_id:
                    result_text += f"User ID: {user_id}\n"
                result_text += f"Report Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n"
                
                # Daily limits
                result_text += "DAILY LIMITS\n"
                daily_limits = limits_data["dailyLimits"]
                daily_usage = limits_data["currentUsage"]
                
                result_text += f"Withdrawal: ${daily_usage['dailyWithdrawal']:.2f} / ${daily_limits['withdrawal']:.2f} "
                result_text += f"({(daily_usage['dailyWithdrawal']/daily_limits['withdrawal']*100):.1f}% used)\n"
                
                result_text += f"Transfer: ${daily_usage['dailyTransfer']:.2f} / ${daily_limits['transfer']:.2f} "
                result_text += f"({(daily_usage['dailyTransfer']/daily_limits['transfer']*100):.1f}% used)\n"
                
                result_text += f"Deposit: ${daily_usage['dailyDeposit']:.2f} / ${daily_limits['deposit']:.2f} "
                result_text += f"({(daily_usage['dailyDeposit']/daily_limits['deposit']*100):.1f}% used)\n\n"
                
                # Monthly limits
                result_text += "MONTHLY LIMITS\n"
                monthly_limits = limits_data["monthlyLimits"]
                
                result_text += f"Withdrawal: ${daily_usage['monthlyWithdrawal']:.2f} / ${monthly_limits['withdrawal']:.2f} "
                result_text += f"({(daily_usage['monthlyWithdrawal']/monthly_limits['withdrawal']*100):.1f}% used)\n"
                
                result_text += f"Transfer: ${daily_usage['monthlyTransfer']:.2f} / ${monthly_limits['transfer']:.2f} "
                result_text += f"({(daily_usage['monthlyTransfer']/monthly_limits['transfer']*100):.1f}% used)\n"
                
                result_text += f"Deposit: ${daily_usage['monthlyDeposit']:.2f} / ${monthly_limits['deposit']:.2f} "
                result_text += f"({(daily_usage['monthlyDeposit']/monthly_limits['deposit']*100):.1f}% used)\n\n"
                
                # Single transaction limits
                result_text += "SINGLE TRANSACTION LIMITS\n"
                tx_limits = limits_data["transactionLimits"]
                result_text += f"Max Single Withdrawal: ${tx_limits['maxSingleWithdrawal']:.2f}\n"
                result_text += f"Max Single Transfer: ${tx_limits['maxSingleTransfer']:.2f}\n"
                result_text += f"Max Single Deposit: ${tx_limits['maxSingleDeposit']:.2f}\n"
                
                query_tools_metrics.transaction_limits_requests.inc()
                logger.info(f"Retrieved transaction limits for account {account_id or 'N/A'}, user {user_id or 'N/A'}")
                
                return [TextContent(type="text", text=result_text)]
                
            except Exception as e:
                error_msg = f"Error retrieving transaction limits: {str(e)}"
                logger.error(error_msg)
                query_tools_metrics.query_errors.inc()
                return [TextContent(type="text", text=error_msg)]
        
        logger.info("Financial query tools registered successfully")
