"""
Account management MCP tools.
"""

import logging
import uuid
from typing import List, Dict, Any, Optional
from decimal import Decimal
from datetime import datetime

from mcp.types import Tool, TextContent
from mcp.server.fastmcp import FastMCP
from pydantic import ValidationError

from ..auth.jwt_handler import JWTAuthHandler, UserContext, AuthenticationError
from ..auth.permissions import PermissionChecker, Permission
from ..clients.account_client import AccountServiceClient
from ..models import (
    AccountCreateRequest,
    AccountUpdateRequest,
    BalanceUpdateRequest,
    AccountSearchRequest,
    AccountResponse,
    BalanceResponse,
    AccountAnalyticsResponse,
    AccountSearchResponse,
    MCPErrorResponse,
    MCPSuccessResponse
)
from ..utils.validation import AccountValidator, InputSanitizer
from ..utils.metrics import account_operations_counter, account_operation_duration

logger = logging.getLogger(__name__)


class AccountTools:
    """Account management MCP tools implementation."""
    
    def __init__(
        self, 
        app: FastMCP, 
        account_client: AccountServiceClient,
        auth_handler: JWTAuthHandler
    ):
        self.app = app
        self.account_client = account_client
        self.auth_handler = auth_handler
        self._register_tools()
        
    def _register_tools(self) -> None:
        """Register all account management tools."""
        logger.info("Registering account management MCP tools")
        
        @self.app.tool()
        async def create_account(
            owner_id: str,
            account_type: str,
            initial_balance: float = 0.0,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Create a new financial account.
            
            Args:
                owner_id: Account owner identifier
                account_type: Type of account (CHECKING, SAVINGS, CREDIT)
                initial_balance: Initial account balance (default: 0.0)
                auth_token: JWT authentication token
                
            Returns:
                Account creation result
            """
            request_id = str(uuid.uuid4())
            
            try:
                with account_operation_duration.labels(operation="create_account").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Check permissions
                    if not PermissionChecker.can_create_account(user_context, owner_id):
                        account_operations_counter.labels(
                            operation="create_account", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to create account",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Validate request
                    try:
                        request_data = AccountCreateRequest(
                            owner_id=owner_id,
                            account_type=account_type,
                            initial_balance=Decimal(str(initial_balance))
                        )
                    except ValidationError as e:
                        account_operations_counter.labels(
                            operation="create_account", 
                            status="validation_error"
                        ).inc()
                        
                        # Convert validation errors to serializable format
                        validation_errors = []
                        for error in e.errors():
                            validation_errors.append({
                                "field": ".".join(str(loc) for loc in error["loc"]),
                                "message": error["msg"],
                                "type": error["type"]
                            })
                        
                        error_response = MCPErrorResponse(
                            error_code="VALIDATION_ERROR",
                            error_message="Invalid request parameters",
                            details={"validation_errors": validation_errors},
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Create account through service
                    account_data = {
                        "ownerId": request_data.owner_id,
                        "accountType": request_data.account_type.value,
                        "balance": float(request_data.initial_balance)
                    }
                    
                    result = await self.account_client.create_account(account_data, auth_token)
                    
                    account_operations_counter.labels(
                        operation="create_account", 
                        status="success"
                    ).inc()
                    
                    success_response = MCPSuccessResponse(
                        message=f"Account created successfully",
                        data=result,
                        request_id=request_id
                    )
                    
                    return [TextContent(type="text", text=success_response.model_dump_json())]
                    
            except AuthenticationError as e:
                account_operations_counter.labels(
                    operation="create_account", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error creating account: {e}", exc_info=True)
                account_operations_counter.labels(
                    operation="create_account", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to create account: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        @self.app.tool()
        async def get_account(
            account_id: str,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Retrieve account details by ID.
            
            Args:
                account_id: Account identifier
                auth_token: JWT authentication token
                
            Returns:
                Account details
            """
            request_id = str(uuid.uuid4())
            
            try:
                with account_operation_duration.labels(operation="get_account").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Get account details first to check ownership
                    account_data = await self.account_client.get_account(account_id, auth_token)
                    
                    # Check permissions
                    if not PermissionChecker.can_access_account(user_context, account_data.get("ownerId")):
                        account_operations_counter.labels(
                            operation="get_account", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to access account",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
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
                    
            except AuthenticationError as e:
                account_operations_counter.labels(
                    operation="get_account", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error retrieving account: {e}", exc_info=True)
                account_operations_counter.labels(
                    operation="get_account", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to retrieve account: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        @self.app.tool()
        async def update_account(
            account_id: str,
            account_type: str = None,
            status: str = None,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Update account information.
            
            Args:
                account_id: Account identifier
                account_type: Updated account type (optional)
                status: Updated account status (optional)
                auth_token: JWT authentication token
                
            Returns:
                Account update result
            """
            request_id = str(uuid.uuid4())
            
            try:
                with account_operation_duration.labels(operation="update_account").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Check permissions
                    if not PermissionChecker.has_permission(user_context, Permission.ACCOUNT_UPDATE):
                        account_operations_counter.labels(
                            operation="update_account", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to update account",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Build update data
                    update_data = {}
                    if account_type:
                        update_data["accountType"] = account_type
                    if status:
                        update_data["status"] = status
                    
                    if not update_data:
                        error_response = MCPErrorResponse(
                            error_code="VALIDATION_ERROR",
                            error_message="No update fields provided",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Update account through service
                    result = await self.account_client.update_account(account_id, update_data, auth_token)
                    
                    account_operations_counter.labels(
                        operation="update_account", 
                        status="success"
                    ).inc()
                    
                    success_response = MCPSuccessResponse(
                        message="Account updated successfully",
                        data=result,
                        request_id=request_id
                    )
                    
                    return [TextContent(type="text", text=success_response.model_dump_json())]
                    
            except AuthenticationError as e:
                account_operations_counter.labels(
                    operation="update_account", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error updating account: {e}", exc_info=True)
                account_operations_counter.labels(
                    operation="update_account", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to update account: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        @self.app.tool()
        async def delete_account(
            account_id: str,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Delete/close an account.
            
            Args:
                account_id: Account identifier
                auth_token: JWT authentication token
                
            Returns:
                Account deletion result
            """
            request_id = str(uuid.uuid4())
            
            try:
                with account_operation_duration.labels(operation="delete_account").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Check permissions
                    if not PermissionChecker.has_permission(user_context, Permission.ACCOUNT_DELETE):
                        account_operations_counter.labels(
                            operation="delete_account", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to delete account",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Delete account through service
                    result = await self.account_client.delete_account(account_id, auth_token)
                    
                    account_operations_counter.labels(
                        operation="delete_account", 
                        status="success"
                    ).inc()
                    
                    success_response = MCPSuccessResponse(
                        message="Account deleted successfully",
                        data=result,
                        request_id=request_id
                    )
                    
                    return [TextContent(type="text", text=success_response.model_dump_json())]
                    
            except AuthenticationError as e:
                account_operations_counter.labels(
                    operation="delete_account", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error deleting account: {e}", exc_info=True)
                account_operations_counter.labels(
                    operation="delete_account", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to delete account: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        @self.app.tool()
        async def get_account_balance(
            account_id: str,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Get current account balance.
            
            Args:
                account_id: Account identifier
                auth_token: JWT authentication token
                
            Returns:
                Account balance information
            """
            request_id = str(uuid.uuid4())
            
            try:
                with account_operation_duration.labels(operation="get_balance").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Get account details first to check ownership
                    account_data = await self.account_client.get_account(account_id, auth_token)
                    
                    # Check permissions
                    if not PermissionChecker.can_access_account(user_context, account_data.get("ownerId")):
                        account_operations_counter.labels(
                            operation="get_balance", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to access account balance",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Get balance through service
                    balance_data = await self.account_client.get_account_balance(account_id, auth_token)
                    
                    account_operations_counter.labels(
                        operation="get_balance", 
                        status="success"
                    ).inc()
                    
                    success_response = MCPSuccessResponse(
                        message="Account balance retrieved successfully",
                        data=balance_data,
                        request_id=request_id
                    )
                    
                    return [TextContent(type="text", text=success_response.model_dump_json())]
                    
            except AuthenticationError as e:
                account_operations_counter.labels(
                    operation="get_balance", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error retrieving balance: {e}", exc_info=True)
                account_operations_counter.labels(
                    operation="get_balance", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to retrieve balance: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        @self.app.tool()
        async def update_account_balance(
            account_id: str,
            new_balance: float,
            reason: str = None,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Update account balance.
            
            Args:
                account_id: Account identifier
                new_balance: New account balance
                reason: Reason for balance update (optional)
                auth_token: JWT authentication token
                
            Returns:
                Balance update result
            """
            request_id = str(uuid.uuid4())
            
            try:
                with account_operation_duration.labels(operation="update_balance").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Check permissions
                    if not PermissionChecker.has_permission(user_context, Permission.ACCOUNT_BALANCE_UPDATE):
                        account_operations_counter.labels(
                            operation="update_balance", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to update account balance",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Validate request
                    try:
                        request_data = BalanceUpdateRequest(
                            new_balance=Decimal(str(new_balance)),
                            reason=reason
                        )
                    except ValidationError as e:
                        account_operations_counter.labels(
                            operation="update_balance", 
                            status="validation_error"
                        ).inc()
                        
                        # Convert validation errors to serializable format
                        validation_errors = []
                        for error in e.errors():
                            validation_errors.append({
                                "field": ".".join(str(loc) for loc in error["loc"]),
                                "message": error["msg"],
                                "type": error["type"]
                            })
                        
                        error_response = MCPErrorResponse(
                            error_code="VALIDATION_ERROR",
                            error_message="Invalid balance update parameters",
                            details={"validation_errors": validation_errors},
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Update balance through service
                    balance_data = {
                        "balance": float(request_data.new_balance),
                        "reason": request_data.reason
                    }
                    
                    result = await self.account_client.update_account_balance(account_id, balance_data, auth_token)
                    
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
                    
            except AuthenticationError as e:
                account_operations_counter.labels(
                    operation="update_balance", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error updating balance: {e}", exc_info=True)
                account_operations_counter.labels(
                    operation="update_balance", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to update balance: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        logger.info("Account management MCP tools registered successfully")