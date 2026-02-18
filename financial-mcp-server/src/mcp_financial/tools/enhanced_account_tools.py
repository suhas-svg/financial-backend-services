"""
Enhanced account management MCP tools with comprehensive error handling.
"""

import logging
import uuid
from typing import List, Dict, Any, Optional
from decimal import Decimal
from datetime import datetime

from mcp.types import Tool, TextContent
from mcp.server.fastmcp import FastMCP

from ..auth.jwt_handler import JWTAuthHandler, UserContext
from ..auth.permissions import PermissionChecker
from ..clients.account_client import AccountServiceClient
from ..models import (
    AccountCreateRequest,
    AccountUpdateRequest,
    BalanceUpdateRequest,
    MCPSuccessResponse
)
from ..exceptions.base import (
    ValidationError,
    AuthenticationError,
    AuthorizationError,
    ServiceError,
    BusinessRuleError
)
from ..exceptions.handlers import (
    ErrorHandler,
    ErrorContext,
    create_error_response,
    safe_execute,
    error_handling_context
)
from ..utils.validation import ComprehensiveValidator
from ..utils.metrics import (
    MetricsCollector,
    account_operations_counter,
    account_operation_duration,
    track_mcp_request
)
from ..utils.error_logging import log_mcp_tool_error

logger = logging.getLogger(__name__)


class EnhancedAccountTools:
    """Enhanced account management MCP tools with comprehensive error handling."""
    
    def __init__(
        self, 
        app: FastMCP, 
        account_client: AccountServiceClient,
        auth_handler: JWTAuthHandler
    ):
        self.app = app
        self.account_client = account_client
        self.auth_handler = auth_handler
        self.validator = ComprehensiveValidator()
        self.error_handler = ErrorHandler()
        self._register_tools()
        
    def _register_tools(self) -> None:
        """Register all account management tools with enhanced error handling."""
        logger.info("Registering enhanced account management MCP tools")
        
        @self.app.tool()
        @track_mcp_request("create_account")
        async def create_account(
            owner_id: str,
            account_type: str,
            initial_balance: float = 0.0,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Create a new financial account with comprehensive validation and error handling.
            
            Args:
                owner_id: Account owner identifier
                account_type: Type of account (CHECKING, SAVINGS, CREDIT, INVESTMENT)
                initial_balance: Initial account balance (default: 0.0)
                auth_token: JWT authentication token
                
            Returns:
                Account creation result or error response
            """
            return await safe_execute(
                "create_account",
                self._create_account_impl,
                owner_id=owner_id,
                account_type=account_type,
                initial_balance=initial_balance,
                auth_token=auth_token
            )
            
        @self.app.tool()
        @track_mcp_request("get_account")
        async def get_account(
            account_id: str,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Retrieve account details by ID with enhanced error handling.
            
            Args:
                account_id: Account identifier
                auth_token: JWT authentication token
                
            Returns:
                Account details or error response
            """
            return await safe_execute(
                "get_account",
                self._get_account_impl,
                account_id=account_id,
                auth_token=auth_token
            )
            
        @self.app.tool()
        @track_mcp_request("update_account_balance")
        async def update_account_balance(
            account_id: str,
            new_balance: float,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Update account balance with business rule validation.
            
            Args:
                account_id: Account identifier
                new_balance: New account balance
                auth_token: JWT authentication token
                
            Returns:
                Balance update result or error response
            """
            return await safe_execute(
                "update_account_balance",
                self._update_balance_impl,
                account_id=account_id,
                new_balance=new_balance,
                auth_token=auth_token
            )
            
    async def _create_account_impl(
        self,
        owner_id: str,
        account_type: str,
        initial_balance: float,
        auth_token: str
    ) -> List[TextContent]:
        """Implementation of create account with comprehensive error handling."""
        request_id = str(uuid.uuid4())
        
        with error_handling_context(
            "create_account",
            request_id=request_id
        ) as ctx:
            # Step 1: Validate and sanitize input parameters
            params = {
                "owner_id": owner_id,
                "account_type": account_type,
                "initial_balance": initial_balance,
                "auth_token": auth_token
            }
            
            schema = {
                "required": ["owner_id", "account_type", "auth_token"],
                "properties": {
                    "owner_id": {
                        "type": "string",
                        "minLength": 1,
                        "maxLength": 100,
                        "pattern": r"^[a-zA-Z0-9_-]+$"
                    },
                    "account_type": {
                        "type": "string",
                        "enum": ["CHECKING", "SAVINGS", "CREDIT", "INVESTMENT"]
                    },
                    "initial_balance": {
                        "type": "number",
                        "minimum": -1000000,
                        "maximum": 1000000,
                        "decimalPlaces": 2
                    },
                    "auth_token": {
                        "type": "string",
                        "minLength": 10
                    }
                }
            }
            
            try:
                # Validate authentication first
                user_context = self.auth_handler.extract_user_context(auth_token)
                ctx.user_id = user_context.user_id
                
                # Comprehensive parameter validation
                validated_params = self.validator.validate_tool_request(
                    "create_account",
                    params,
                    schema,
                    user_context,
                    request_id
                )
                
                # Step 2: Check permissions
                if not PermissionChecker.can_create_account(user_context, owner_id):
                    raise AuthorizationError(
                        message="Insufficient permissions to create account",
                        resource="account",
                        action="create",
                        details={"owner_id": owner_id, "user_id": user_context.user_id}
                    )
                    
                # Step 3: Business rule validation
                self.validator.business_validator.validate_account_creation_rules(
                    validated_params["owner_id"],
                    validated_params["account_type"],
                    validated_params["initial_balance"],
                    user_context
                )
                
                # Step 4: Create account through service
                account_data = {
                    "ownerId": validated_params["owner_id"],
                    "accountType": validated_params["account_type"],
                    "balance": float(validated_params["initial_balance"])
                }
                
                result = await self.account_client.create_account(account_data, auth_token)
                
                # Step 5: Record success metrics
                account_operations_counter.labels(
                    operation="create_account", 
                    status="success"
                ).inc()
                
                # Step 6: Return success response
                success_response = MCPSuccessResponse(
                    message="Account created successfully",
                    data=result,
                    request_id=request_id
                )
                
                return [TextContent(type="text", text=success_response.model_dump_json())]
                
            except (ValidationError, AuthenticationError, AuthorizationError, BusinessRuleError) as e:
                # Log structured error
                log_mcp_tool_error(e, "create_account", ctx.user_id, request_id, params)
                
                # Record error metrics
                account_operations_counter.labels(
                    operation="create_account", 
                    status=e.error_code.lower()
                ).inc()
                
                MetricsCollector.record_error(type(e).__name__, "account_tools")
                
                # Return structured error response
                error_response = self.error_handler.handle_error(e, ctx)
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except ServiceError as e:
                # Log service error with additional context
                log_mcp_tool_error(e, "create_account", ctx.user_id, request_id, params)
                
                account_operations_counter.labels(
                    operation="create_account", 
                    status="service_error"
                ).inc()
                
                # Handle service-specific errors
                if e.status_code == 409:
                    # Convert to business rule error for duplicate accounts
                    business_error = BusinessRuleError(
                        message="Account already exists for this owner",
                        rule="unique_account_per_owner",
                        details=e.details
                    )
                    error_response = self.error_handler.handle_error(business_error, ctx)
                else:
                    error_response = self.error_handler.handle_error(e, ctx)
                    
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                # Log unexpected error with full context
                log_mcp_tool_error(e, "create_account", ctx.user_id, request_id, params)
                
                account_operations_counter.labels(
                    operation="create_account", 
                    status="internal_error"
                ).inc()
                
                # Handle unexpected errors
                error_response = self.error_handler.handle_error(e, ctx, include_traceback=True)
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
    async def _get_account_impl(
        self,
        account_id: str,
        auth_token: str
    ) -> List[TextContent]:
        """Implementation of get account with enhanced error handling."""
        request_id = str(uuid.uuid4())
        
        with error_handling_context(
            "get_account",
            request_id=request_id
        ) as ctx:
            try:
                # Validate authentication
                user_context = self.auth_handler.extract_user_context(auth_token)
                ctx.user_id = user_context.user_id
                
                # Validate account ID format
                params = {"account_id": account_id}
                schema = {
                    "required": ["account_id"],
                    "properties": {
                        "account_id": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 50,
                            "pattern": r"^[a-zA-Z0-9_-]+$"
                        }
                    }
                }
                
                validated_params = self.validator.validate_tool_request(
                    "get_account",
                    params,
                    schema,
                    user_context,
                    request_id
                )
                
                # Get account details
                account_data = await self.account_client.get_account(
                    validated_params["account_id"], 
                    auth_token
                )
                
                # Check permissions after getting account data
                if not PermissionChecker.can_access_account(user_context, account_data.get("ownerId")):
                    raise AuthorizationError(
                        message="Access denied to account",
                        resource="account",
                        action="read",
                        details={"account_id": account_id}
                    )
                    
                # Record success
                account_operations_counter.labels(
                    operation="get_account", 
                    status="success"
                ).inc()
                
                success_response = MCPSuccessResponse(
                    message="Account retrieved successfully",
                    data=account_data,
                    request_id=request_id
                )
                
                return [TextContent(type="text", text=success_response.model_dump_json())]
                
            except Exception as e:
                log_mcp_tool_error(e, "get_account", ctx.user_id, request_id, {"account_id": account_id})
                
                account_operations_counter.labels(
                    operation="get_account", 
                    status=getattr(e, 'error_code', 'error').lower()
                ).inc()
                
                error_response = self.error_handler.handle_error(e, ctx)
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
    async def _update_balance_impl(
        self,
        account_id: str,
        new_balance: float,
        auth_token: str
    ) -> List[TextContent]:
        """Implementation of update balance with enhanced error handling."""
        request_id = str(uuid.uuid4())
        
        with error_handling_context(
            "update_account_balance",
            request_id=request_id
        ) as ctx:
            try:
                # Validate authentication
                user_context = self.auth_handler.extract_user_context(auth_token)
                ctx.user_id = user_context.user_id
                
                # Validate parameters
                params = {"account_id": account_id, "new_balance": new_balance}
                schema = {
                    "required": ["account_id", "new_balance"],
                    "properties": {
                        "account_id": {
                            "type": "string",
                            "minLength": 1,
                            "maxLength": 50
                        },
                        "new_balance": {
                            "type": "number",
                            "minimum": -1000000,
                            "maximum": 1000000,
                            "decimalPlaces": 2
                        }
                    }
                }
                
                validated_params = self.validator.validate_tool_request(
                    "update_account_balance",
                    params,
                    schema,
                    user_context,
                    request_id
                )
                
                # Get current account to check permissions and validate business rules
                account_data = await self.account_client.get_account(account_id, auth_token)
                
                if not PermissionChecker.can_modify_account(user_context, account_data.get("ownerId")):
                    raise AuthorizationError(
                        message="Insufficient permissions to modify account balance",
                        resource="account",
                        action="update_balance",
                        details={"account_id": account_id}
                    )
                    
                # Business rule validation for balance updates
                current_balance = Decimal(str(account_data.get("balance", 0)))
                new_balance_decimal = validated_params["new_balance"]
                balance_change = new_balance_decimal - current_balance
                
                # Check for large balance changes
                if abs(balance_change) > Decimal("50000.00"):
                    raise BusinessRuleError(
                        message=f"Balance change of ${abs(balance_change)} exceeds maximum allowed",
                        rule="max_balance_change",
                        details={
                            "current_balance": float(current_balance),
                            "new_balance": float(new_balance_decimal),
                            "change_amount": float(balance_change)
                        }
                    )
                    
                # Update balance
                result = await self.account_client.update_balance(
                    account_id,
                    new_balance_decimal,
                    auth_token
                )
                
                account_operations_counter.labels(
                    operation="update_balance", 
                    status="success"
                ).inc()
                
                success_response = MCPSuccessResponse(
                    message="Account balance updated successfully",
                    data=result,
                    request_id=request_id
                )
                
                return [TextContent(type="text", text=success_response.model_dump_json())]
                
            except Exception as e:
                log_mcp_tool_error(
                    e, 
                    "update_account_balance", 
                    ctx.user_id, 
                    request_id, 
                    {"account_id": account_id, "new_balance": new_balance}
                )
                
                account_operations_counter.labels(
                    operation="update_balance", 
                    status=getattr(e, 'error_code', 'error').lower()
                ).inc()
                
                error_response = self.error_handler.handle_error(e, ctx)
                return [TextContent(type="text", text=error_response.model_dump_json())]