"""
Transaction Service HTTP client with error handling.
"""

import logging
from typing import Dict, Any, Optional, List
from decimal import Decimal
from datetime import datetime

from .base_client import BaseHTTPClient

logger = logging.getLogger(__name__)


class TransactionServiceClient(BaseHTTPClient):
    """HTTP client for Transaction Service integration."""
    
    def __init__(self, base_url: str, timeout: int = 5000, **kwargs):
        super().__init__(base_url, timeout, **kwargs)
        
    async def create_transaction(
        self, 
        transaction_data: Dict[str, Any], 
        auth_token: str
    ) -> Dict[str, Any]:
        """
        Create a new transaction.
        
        Args:
            transaction_data: Transaction creation data
            auth_token: JWT authentication token
            
        Returns:
            Created transaction data
        """
        tx_type = str(
            transaction_data.get("type")
            or transaction_data.get("transactionType")
            or ""
        ).upper()

        logger.info(f"Creating transaction via typed endpoint: {tx_type or 'unknown'}")

        if tx_type == "DEPOSIT":
            payload = {
                "accountId": transaction_data.get("accountId"),
                "amount": transaction_data.get("amount"),
                "description": transaction_data.get("description"),
                "reference": transaction_data.get("reference"),
                "currency": transaction_data.get("currency", "USD"),
            }
            return await self.post("/api/transactions/deposit", data=payload, auth_token=auth_token)

        if tx_type == "WITHDRAWAL":
            payload = {
                "accountId": transaction_data.get("accountId"),
                "amount": transaction_data.get("amount"),
                "description": transaction_data.get("description"),
                "reference": transaction_data.get("reference"),
                "currency": transaction_data.get("currency", "USD"),
            }
            return await self.post("/api/transactions/withdraw", data=payload, auth_token=auth_token)

        if tx_type == "TRANSFER":
            payload = {
                "fromAccountId": transaction_data.get("fromAccountId"),
                "toAccountId": transaction_data.get("toAccountId"),
                "amount": transaction_data.get("amount"),
                "description": transaction_data.get("description"),
                "reference": transaction_data.get("reference"),
                "currency": transaction_data.get("currency", "USD"),
            }
            return await self.post("/api/transactions/transfer", data=payload, auth_token=auth_token)

        raise ValueError(
            "Unsupported transaction type. Use one of: DEPOSIT, WITHDRAWAL, TRANSFER."
        )
        
    async def get_transaction(self, transaction_id: str, auth_token: str) -> Dict[str, Any]:
        """
        Get transaction details by ID.
        
        Args:
            transaction_id: Transaction identifier
            auth_token: JWT authentication token
            
        Returns:
            Transaction data dictionary
        """
        logger.info(f"Fetching transaction details for ID: {transaction_id}")
        return await self.get(f"/api/transactions/{transaction_id}", auth_token=auth_token)
        
    async def update_transaction(
        self, 
        transaction_id: str, 
        transaction_data: Dict[str, Any], 
        auth_token: str
    ) -> Dict[str, Any]:
        """
        Update transaction information.
        
        Args:
            transaction_id: Transaction identifier
            transaction_data: Updated transaction data
            auth_token: JWT authentication token
            
        Returns:
            Updated transaction data
        """
        raise NotImplementedError("Transaction update endpoint is not exposed by transaction-service")
        
    async def delete_transaction(self, transaction_id: str, auth_token: str) -> Dict[str, Any]:
        """
        Delete a transaction.
        
        Args:
            transaction_id: Transaction identifier
            auth_token: JWT authentication token
            
        Returns:
            Deletion confirmation
        """
        raise NotImplementedError("Transaction delete endpoint is not exposed by transaction-service")
        
    async def deposit_funds(
        self, 
        account_id: str, 
        amount: Decimal, 
        description: Optional[str] = None,
        auth_token: str = None
    ) -> Dict[str, Any]:
        """
        Make a deposit to an account.
        
        Args:
            account_id: Target account identifier
            amount: Deposit amount
            description: Transaction description
            auth_token: JWT authentication token
            
        Returns:
            Transaction data
        """
        request_data = {
            "accountId": account_id,
            "amount": float(amount),
            "description": description or f"Deposit to account {account_id}",
            "currency": "USD",
        }
        
        logger.info(f"Processing deposit of {amount} to account {account_id}")
        return await self.post("/api/transactions/deposit", data=request_data, auth_token=auth_token)
        
    async def withdraw_funds(
        self, 
        account_id: str, 
        amount: Decimal, 
        description: Optional[str] = None,
        auth_token: str = None
    ) -> Dict[str, Any]:
        """
        Make a withdrawal from an account.
        
        Args:
            account_id: Source account identifier
            amount: Withdrawal amount
            description: Transaction description
            auth_token: JWT authentication token
            
        Returns:
            Transaction data
        """
        request_data = {
            "accountId": account_id,
            "amount": float(amount),
            "description": description or f"Withdrawal from account {account_id}",
            "currency": "USD",
        }
        
        logger.info(f"Processing withdrawal of {amount} from account {account_id}")
        return await self.post("/api/transactions/withdraw", data=request_data, auth_token=auth_token)
        
    async def transfer_funds(
        self, 
        from_account_id: str, 
        to_account_id: str, 
        amount: Decimal, 
        description: Optional[str] = None,
        auth_token: str = None
    ) -> Dict[str, Any]:
        """
        Transfer funds between accounts.
        
        Args:
            from_account_id: Source account identifier
            to_account_id: Destination account identifier
            amount: Transfer amount
            description: Transaction description
            auth_token: JWT authentication token
            
        Returns:
            Transfer transaction data
        """
        transaction_data = {
            "fromAccountId": from_account_id,
            "toAccountId": to_account_id,
            "amount": float(amount),
            "description": description or f"Transfer from {from_account_id} to {to_account_id}",
            "currency": "USD",
        }
        
        logger.info(f"Processing transfer of {amount} from {from_account_id} to {to_account_id}")
        return await self.post("/api/transactions/transfer", data=transaction_data, auth_token=auth_token)
        
    async def reverse_transaction(
        self, 
        transaction_id: str, 
        reason: Optional[str] = None,
        auth_token: str = None
    ) -> Dict[str, Any]:
        """
        Reverse a transaction.
        
        Args:
            transaction_id: Transaction to reverse
            reason: Reason for reversal
            auth_token: JWT authentication token
            
        Returns:
            Reversal transaction data
        """
        reversal_data = {
            "reason": reason or "Transaction reversal"
        }
        
        logger.info(f"Reversing transaction: {transaction_id}")
        return await self.post(
            f"/api/transactions/{transaction_id}/reverse", 
            data=reversal_data, 
            auth_token=auth_token
        )
        
    async def get_transaction_history(
        self, 
        account_id: str, 
        page: int = 0, 
        size: int = 20,
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        auth_token: str = None
    ) -> Dict[str, Any]:
        """
        Get transaction history for an account.
        
        Args:
            account_id: Account identifier
            page: Page number (0-based)
            size: Page size
            start_date: Start date filter (ISO format)
            end_date: End date filter (ISO format)
            auth_token: JWT authentication token
            
        Returns:
            Paginated transaction history
        """
        params = {
            "accountId": account_id,
            "page": page,
            "size": size
        }
        
        if start_date:
            params["startDate"] = start_date
        if end_date:
            params["endDate"] = end_date
            
        logger.info(f"Fetching transaction history for account: {account_id}")
        return await self.get(f"/api/transactions/account/{account_id}", params=params, auth_token=auth_token)
        
    async def search_transactions(
        self, 
        search_params: Dict[str, Any], 
        auth_token: str
    ) -> Dict[str, Any]:
        """
        Search transactions with filters.
        
        Args:
            search_params: Search parameters (accountId, amount, type, etc.)
            auth_token: JWT authentication token
            
        Returns:
            Search results with pagination info
        """
        logger.info(f"Searching transactions with params: {search_params}")
        return await self.get("/api/transactions/search", params=search_params, auth_token=auth_token)
        
    async def get_transaction_analytics(
        self, 
        account_id: Optional[str] = None,
        start_date: Optional[str] = None,
        end_date: Optional[str] = None,
        auth_token: str = None
    ) -> Dict[str, Any]:
        """
        Get transaction analytics and metrics.
        
        Args:
            account_id: Optional account filter
            start_date: Start date filter (ISO format)
            end_date: End date filter (ISO format)
            auth_token: JWT authentication token
            
        Returns:
            Analytics data
        """
        params = {}
        if start_date:
            params["startDate"] = start_date
        if end_date:
            params["endDate"] = end_date
            
        logger.info("Fetching transaction statistics")
        if account_id:
            return await self.get(
                f"/api/transactions/account/{account_id}/stats",
                params=params,
                auth_token=auth_token
            )
        return await self.get("/api/transactions/user/stats", params=params, auth_token=auth_token)
