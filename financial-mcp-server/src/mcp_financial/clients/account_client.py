"""
Account Service HTTP client with retry logic and circuit breaker.
"""

import logging
from typing import Dict, Any, Optional, List
from decimal import Decimal

from .base_client import BaseHTTPClient

logger = logging.getLogger(__name__)


class AccountServiceClient(BaseHTTPClient):
    """HTTP client for Account Service integration."""
    
    def __init__(self, base_url: str, timeout: int = 5000, **kwargs):
        super().__init__(base_url, timeout, **kwargs)
        
    async def get_account(self, account_id: str, auth_token: str) -> Dict[str, Any]:
        """
        Get account details by ID.
        
        Args:
            account_id: Account identifier
            auth_token: JWT authentication token
            
        Returns:
            Account data dictionary
        """
        logger.info(f"Fetching account details for ID: {account_id}")
        return await self.get(f"/api/accounts/{account_id}", auth_token=auth_token)
        
    async def create_account(self, account_data: Dict[str, Any], auth_token: str) -> Dict[str, Any]:
        """
        Create a new account.
        
        Args:
            account_data: Account creation data
            auth_token: JWT authentication token
            
        Returns:
            Created account data
        """
        logger.info(f"Creating account for owner: {account_data.get('ownerId', 'unknown')}")
        return await self.post("/api/accounts", data=account_data, auth_token=auth_token)
        
    async def update_account(
        self, 
        account_id: str, 
        account_data: Dict[str, Any], 
        auth_token: str
    ) -> Dict[str, Any]:
        """
        Update account information.
        
        Args:
            account_id: Account identifier
            account_data: Updated account data
            auth_token: JWT authentication token
            
        Returns:
            Updated account data
        """
        logger.info(f"Updating account: {account_id}")
        return await self.put(f"/api/accounts/{account_id}", data=account_data, auth_token=auth_token)
        
    async def delete_account(self, account_id: str, auth_token: str) -> Dict[str, Any]:
        """
        Delete/close an account.
        
        Args:
            account_id: Account identifier
            auth_token: JWT authentication token
            
        Returns:
            Deletion confirmation
        """
        logger.info(f"Deleting account: {account_id}")
        return await self.delete(f"/api/accounts/{account_id}", auth_token=auth_token)
        
    async def get_account_balance(self, account_id: str, auth_token: str) -> Dict[str, Any]:
        """
        Get current account balance.
        
        Args:
            account_id: Account identifier
            auth_token: JWT authentication token
            
        Returns:
            Balance information
        """
        logger.info(f"Fetching balance for account: {account_id}")
        account = await self.get_account(account_id, auth_token)
        return {
            "accountId": account.get("id", account_id),
            "ownerId": account.get("ownerId"),
            "accountType": account.get("accountType"),
            "balance": account.get("balance"),
        }
        
    async def update_account_balance(
        self, 
        account_id: str, 
        balance_data: Dict[str, Any], 
        auth_token: str
    ) -> Dict[str, Any]:
        """
        Update account balance.
        
        Args:
            account_id: Account identifier
            balance_data: Balance update data
            auth_token: JWT authentication token
            
        Returns:
            Updated balance information
        """
        logger.info(f"Updating balance for account: {account_id}")
        existing_account = await self.get_account(account_id, auth_token)
        updated_payload: Dict[str, Any] = {
            "accountType": existing_account.get("accountType"),
            "ownerId": existing_account.get("ownerId"),
            "balance": balance_data.get("balance"),
        }

        if "interestRate" in existing_account:
            updated_payload["interestRate"] = existing_account.get("interestRate")
        if "creditLimit" in existing_account:
            updated_payload["creditLimit"] = existing_account.get("creditLimit")
        if "dueDate" in existing_account:
            updated_payload["dueDate"] = existing_account.get("dueDate")

        return await self.put(
            f"/api/accounts/{account_id}",
            data=updated_payload,
            auth_token=auth_token
        )

    async def update_balance(self, account_id: str, new_balance: Decimal, auth_token: str) -> Dict[str, Any]:
        """
        Backward-compatible alias used by enhanced tools.
        """
        return await self.update_account_balance(
            account_id=account_id,
            balance_data={"balance": float(new_balance)},
            auth_token=auth_token
        )
        
    async def get_accounts_by_owner(self, owner_id: str, auth_token: str) -> List[Dict[str, Any]]:
        """
        Get all accounts for a specific owner.
        
        Args:
            owner_id: Account owner identifier
            auth_token: JWT authentication token
            
        Returns:
            List of account data dictionaries
        """
        logger.info(f"Fetching accounts for owner: {owner_id}")
        params = {"ownerId": owner_id}
        response = await self.get("/api/accounts", params=params, auth_token=auth_token)
        
        # Handle both list response and paginated response
        if isinstance(response, list):
            return response
        elif isinstance(response, dict) and "content" in response:
            return response["content"]
        else:
            return []
            
    async def search_accounts(
        self, 
        search_params: Dict[str, Any], 
        auth_token: str
    ) -> Dict[str, Any]:
        """
        Search accounts with filters.
        
        Args:
            search_params: Search parameters (accountType, ownerId, etc.)
            auth_token: JWT authentication token
            
        Returns:
            Search results with pagination info
        """
        logger.info(f"Searching accounts with params: {search_params}")
        return await self.get("/api/accounts", params=search_params, auth_token=auth_token)
        
    async def get_account_analytics(self, account_id: str, auth_token: str) -> Dict[str, Any]:
        """
        Get account analytics and metrics.
        
        Args:
            account_id: Account identifier
            auth_token: JWT authentication token
            
        Returns:
            Analytics data
        """
        logger.info(f"Fetching account analytics snapshot for account: {account_id}")
        account = await self.get_account(account_id, auth_token)
        return {
            "accountId": account.get("id", account_id),
            "ownerId": account.get("ownerId"),
            "accountType": account.get("accountType"),
            "currentBalance": account.get("balance"),
        }
