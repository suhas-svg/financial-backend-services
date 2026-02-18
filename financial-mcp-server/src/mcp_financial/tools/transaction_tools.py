"""
Transaction processing MCP tools.
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
from ..clients.transaction_client import TransactionServiceClient
from ..clients.account_client import AccountServiceClient
from ..models import (
    DepositRequest,
    WithdrawalRequest,
    TransferRequest,
    TransactionReversalRequest,
    TransactionResponse,
    TransactionHistoryResponse,
    TransactionAnalyticsResponse,
    MCPErrorResponse,
    MCPSuccessResponse
)
from ..utils.metrics import (
    transaction_operations_counter, 
    transaction_operation_duration,
    transaction_amounts_histogram
)

logger = logging.getLogger(__name__)


class TransactionTools:
    """Transaction processing MCP tools implementation."""
    
    def __init__(
        self, 
        app: FastMCP, 
        transaction_client: TransactionServiceClient,
        account_client: AccountServiceClient,
        auth_handler: JWTAuthHandler
    ):
        self.app = app
        self.transaction_client = transaction_client
        self.account_client = account_client
        self.auth_handler = auth_handler
        self._register_tools()
        
    def _register_tools(self) -> None:
        """Register all transaction processing tools."""
        logger.info("Registering transaction processing MCP tools")
        
        @self.app.tool()
        async def deposit_funds(
            account_id: str,
            amount: float,
            description: str = None,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Make a deposit to an account.
            
            Args:
                account_id: Target account identifier
                amount: Deposit amount (must be positive)
                description: Transaction description (optional)
                auth_token: JWT authentication token
                
            Returns:
                Deposit transaction result
            """
            request_id = str(uuid.uuid4())
            
            try:
                with transaction_operation_duration.labels(operation="deposit").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Get account details to check ownership
                    account_data = await self.account_client.get_account(account_id, auth_token)
                    
                    # Check permissions
                    if not PermissionChecker.can_perform_transaction(
                        user_context, 
                        account_data.get("ownerId"),
                        "DEPOSIT"
                    ):
                        transaction_operations_counter.labels(
                            operation="deposit", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to perform deposit",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Validate request
                    try:
                        request_data = DepositRequest(
                            account_id=account_id,
                            amount=Decimal(str(amount)),
                            description=description
                        )
                    except ValidationError as e:
                        transaction_operations_counter.labels(
                            operation="deposit", 
                            status="validation_error"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="VALIDATION_ERROR",
                            error_message="Invalid deposit parameters",
                            details={"validation_errors": e.errors()},
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Process deposit through service
                    result = await self.transaction_client.deposit_funds(
                        account_id=request_data.account_id,
                        amount=request_data.amount,
                        description=request_data.description,
                        auth_token=auth_token
                    )
                    
                    # Record metrics
                    transaction_operations_counter.labels(
                        operation="deposit", 
                        status="success"
                    ).inc()
                    transaction_amounts_histogram.labels(
                        transaction_type="DEPOSIT"
                    ).observe(float(request_data.amount))
                    
                    success_response = MCPSuccessResponse(
                        message=f"Deposit of {request_data.amount} processed successfully",
                        data=result,
                        request_id=request_id
                    )
                    
                    return [TextContent(type="text", text=success_response.model_dump_json())]
                    
            except AuthenticationError as e:
                transaction_operations_counter.labels(
                    operation="deposit", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error processing deposit: {e}", exc_info=True)
                transaction_operations_counter.labels(
                    operation="deposit", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to process deposit: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        @self.app.tool()
        async def withdraw_funds(
            account_id: str,
            amount: float,
            description: str = None,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Make a withdrawal from an account.
            
            Args:
                account_id: Source account identifier
                amount: Withdrawal amount (must be positive)
                description: Transaction description (optional)
                auth_token: JWT authentication token
                
            Returns:
                Withdrawal transaction result
            """
            request_id = str(uuid.uuid4())
            
            try:
                with transaction_operation_duration.labels(operation="withdrawal").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Get account details to check ownership and balance
                    account_data = await self.account_client.get_account(account_id, auth_token)
                    
                    # Check permissions
                    if not PermissionChecker.can_perform_transaction(
                        user_context, 
                        account_data.get("ownerId"),
                        "WITHDRAWAL"
                    ):
                        transaction_operations_counter.labels(
                            operation="withdrawal", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to perform withdrawal",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Validate request
                    try:
                        request_data = WithdrawalRequest(
                            account_id=account_id,
                            amount=Decimal(str(amount)),
                            description=description
                        )
                    except ValidationError as e:
                        transaction_operations_counter.labels(
                            operation="withdrawal", 
                            status="validation_error"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="VALIDATION_ERROR",
                            error_message="Invalid withdrawal parameters",
                            details={"validation_errors": e.errors()},
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Check sufficient balance
                    current_balance = Decimal(str(account_data.get("balance", 0)))
                    if current_balance < request_data.amount:
                        transaction_operations_counter.labels(
                            operation="withdrawal", 
                            status="insufficient_funds"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="INSUFFICIENT_FUNDS",
                            error_message=f"Insufficient funds. Available: {current_balance}, Requested: {request_data.amount}",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Process withdrawal through service
                    result = await self.transaction_client.withdraw_funds(
                        account_id=request_data.account_id,
                        amount=request_data.amount,
                        description=request_data.description,
                        auth_token=auth_token
                    )
                    
                    # Record metrics
                    transaction_operations_counter.labels(
                        operation="withdrawal", 
                        status="success"
                    ).inc()
                    transaction_amounts_histogram.labels(
                        transaction_type="WITHDRAWAL"
                    ).observe(float(request_data.amount))
                    
                    success_response = MCPSuccessResponse(
                        message=f"Withdrawal of {request_data.amount} processed successfully",
                        data=result,
                        request_id=request_id
                    )
                    
                    return [TextContent(type="text", text=success_response.model_dump_json())]
                    
            except AuthenticationError as e:
                transaction_operations_counter.labels(
                    operation="withdrawal", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error processing withdrawal: {e}", exc_info=True)
                transaction_operations_counter.labels(
                    operation="withdrawal", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to process withdrawal: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        @self.app.tool()
        async def transfer_funds(
            from_account_id: str,
            to_account_id: str,
            amount: float,
            description: str = None,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Transfer funds between accounts.
            
            Args:
                from_account_id: Source account identifier
                to_account_id: Destination account identifier
                amount: Transfer amount (must be positive)
                description: Transaction description (optional)
                auth_token: JWT authentication token
                
            Returns:
                Transfer transaction result
            """
            request_id = str(uuid.uuid4())
            
            try:
                with transaction_operation_duration.labels(operation="transfer").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Validate request
                    try:
                        request_data = TransferRequest(
                            from_account_id=from_account_id,
                            to_account_id=to_account_id,
                            amount=Decimal(str(amount)),
                            description=description
                        )
                    except ValidationError as e:
                        transaction_operations_counter.labels(
                            operation="transfer", 
                            status="validation_error"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="VALIDATION_ERROR",
                            error_message="Invalid transfer parameters",
                            details={"validation_errors": e.errors()},
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Get source account details to check ownership and balance
                    source_account = await self.account_client.get_account(from_account_id, auth_token)
                    
                    # Check permissions for source account
                    if not PermissionChecker.can_perform_transaction(
                        user_context, 
                        source_account.get("ownerId"),
                        "TRANSFER"
                    ):
                        transaction_operations_counter.labels(
                            operation="transfer", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to transfer from source account",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Verify destination account exists
                    try:
                        await self.account_client.get_account(to_account_id, auth_token)
                    except Exception as e:
                        transaction_operations_counter.labels(
                            operation="transfer", 
                            status="invalid_destination"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="INVALID_DESTINATION",
                            error_message=f"Destination account not found or inaccessible: {to_account_id}",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Check sufficient balance
                    current_balance = Decimal(str(source_account.get("balance", 0)))
                    if current_balance < request_data.amount:
                        transaction_operations_counter.labels(
                            operation="transfer", 
                            status="insufficient_funds"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="INSUFFICIENT_FUNDS",
                            error_message=f"Insufficient funds in source account. Available: {current_balance}, Requested: {request_data.amount}",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Process transfer through service (atomic operation)
                    result = await self.transaction_client.transfer_funds(
                        from_account_id=request_data.from_account_id,
                        to_account_id=request_data.to_account_id,
                        amount=request_data.amount,
                        description=request_data.description,
                        auth_token=auth_token
                    )
                    
                    # Record metrics
                    transaction_operations_counter.labels(
                        operation="transfer", 
                        status="success"
                    ).inc()
                    transaction_amounts_histogram.labels(
                        transaction_type="TRANSFER"
                    ).observe(float(request_data.amount))
                    
                    success_response = MCPSuccessResponse(
                        message=f"Transfer of {request_data.amount} from {from_account_id} to {to_account_id} processed successfully",
                        data=result,
                        request_id=request_id
                    )
                    
                    return [TextContent(type="text", text=success_response.model_dump_json())]
                    
            except AuthenticationError as e:
                transaction_operations_counter.labels(
                    operation="transfer", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error processing transfer: {e}", exc_info=True)
                transaction_operations_counter.labels(
                    operation="transfer", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to process transfer: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        @self.app.tool()
        async def reverse_transaction(
            transaction_id: str,
            reason: str = None,
            auth_token: str = ""
        ) -> List[TextContent]:
            """
            Reverse a transaction.
            
            Args:
                transaction_id: Transaction identifier to reverse
                reason: Reason for reversal (optional)
                auth_token: JWT authentication token
                
            Returns:
                Transaction reversal result
            """
            request_id = str(uuid.uuid4())
            
            try:
                with transaction_operation_duration.labels(operation="reversal").time():
                    # Validate authentication
                    user_context = self.auth_handler.extract_user_context(auth_token)
                    
                    # Check permissions for transaction reversal
                    if not PermissionChecker.can_reverse_transaction(user_context):
                        transaction_operations_counter.labels(
                            operation="reversal", 
                            status="permission_denied"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="PERMISSION_DENIED",
                            error_message="Insufficient permissions to reverse transactions",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Validate request
                    try:
                        request_data = TransactionReversalRequest(
                            transaction_id=transaction_id,
                            reason=reason
                        )
                    except ValidationError as e:
                        transaction_operations_counter.labels(
                            operation="reversal", 
                            status="validation_error"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="VALIDATION_ERROR",
                            error_message="Invalid reversal parameters",
                            details={"validation_errors": e.errors()},
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Get original transaction details for audit logging
                    try:
                        original_transaction = await self.transaction_client.get_transaction(
                            transaction_id, auth_token
                        )
                    except Exception as e:
                        transaction_operations_counter.labels(
                            operation="reversal", 
                            status="transaction_not_found"
                        ).inc()
                        
                        error_response = MCPErrorResponse(
                            error_code="TRANSACTION_NOT_FOUND",
                            error_message=f"Original transaction not found: {transaction_id}",
                            request_id=request_id
                        )
                        return [TextContent(type="text", text=error_response.model_dump_json())]
                    
                    # Process reversal through service
                    result = await self.transaction_client.reverse_transaction(
                        transaction_id=request_data.transaction_id,
                        reason=request_data.reason,
                        auth_token=auth_token
                    )
                    
                    # Record metrics
                    transaction_operations_counter.labels(
                        operation="reversal", 
                        status="success"
                    ).inc()
                    
                    # Log audit trail
                    logger.info(
                        f"Transaction reversed",
                        extra={
                            "original_transaction_id": transaction_id,
                            "reversal_transaction_id": result.get("transactionId"),
                            "user_id": user_context.user_id,
                            "reason": request_data.reason,
                            "original_amount": original_transaction.get("amount"),
                            "request_id": request_id
                        }
                    )
                    
                    success_response = MCPSuccessResponse(
                        message=f"Transaction {transaction_id} reversed successfully",
                        data=result,
                        request_id=request_id
                    )
                    
                    return [TextContent(type="text", text=success_response.model_dump_json())]
                    
            except AuthenticationError as e:
                transaction_operations_counter.labels(
                    operation="reversal", 
                    status="auth_error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="AUTHENTICATION_ERROR",
                    error_message=str(e),
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
                
            except Exception as e:
                logger.error(f"Error reversing transaction: {e}", exc_info=True)
                transaction_operations_counter.labels(
                    operation="reversal", 
                    status="error"
                ).inc()
                
                error_response = MCPErrorResponse(
                    error_code="INTERNAL_ERROR",
                    error_message=f"Failed to reverse transaction: {str(e)}",
                    request_id=request_id
                )
                return [TextContent(type="text", text=error_response.model_dump_json())]
        
        logger.info("Transaction processing MCP tools registered successfully")
