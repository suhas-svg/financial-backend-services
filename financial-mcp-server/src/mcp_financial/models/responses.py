"""
Response models for MCP tools.
"""

from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from decimal import Decimal
from datetime import datetime


class AccountResponse(BaseModel):
    """Response model for account data."""
    
    id: str = Field(..., description="Account identifier")
    owner_id: str = Field(..., description="Account owner identifier")
    account_type: str = Field(..., description="Account type")
    balance: Decimal = Field(..., description="Current account balance")
    status: str = Field(..., description="Account status")
    created_at: datetime = Field(..., description="Account creation timestamp")
    updated_at: Optional[datetime] = Field(None, description="Last update timestamp")


class BalanceResponse(BaseModel):
    """Response model for balance information."""
    
    account_id: str = Field(..., description="Account identifier")
    balance: Decimal = Field(..., description="Current balance")
    available_balance: Optional[Decimal] = Field(None, description="Available balance")
    pending_balance: Optional[Decimal] = Field(None, description="Pending balance")
    last_updated: datetime = Field(..., description="Last balance update timestamp")


class AccountAnalyticsResponse(BaseModel):
    """Response model for account analytics."""
    
    account_id: str = Field(..., description="Account identifier")
    total_deposits: Decimal = Field(..., description="Total deposits amount")
    total_withdrawals: Decimal = Field(..., description="Total withdrawals amount")
    transaction_count: int = Field(..., description="Total number of transactions")
    average_balance: Decimal = Field(..., description="Average balance over time")
    balance_trend: str = Field(..., description="Balance trend (increasing/decreasing/stable)")
    last_transaction_date: Optional[datetime] = Field(None, description="Last transaction date")


class AccountSearchResponse(BaseModel):
    """Response model for account search results."""
    
    content: List[AccountResponse] = Field(..., description="List of accounts")
    total_elements: int = Field(..., description="Total number of accounts")
    total_pages: int = Field(..., description="Total number of pages")
    current_page: int = Field(..., description="Current page number")
    page_size: int = Field(..., description="Page size")
    has_next: bool = Field(..., description="Whether there are more pages")
    has_previous: bool = Field(..., description="Whether there are previous pages")


class MCPErrorResponse(BaseModel):
    """Response model for MCP errors."""
    
    error_code: str = Field(..., description="Error code")
    error_message: str = Field(..., description="Human-readable error message")
    details: Optional[Dict[str, Any]] = Field(None, description="Additional error details")
    timestamp: datetime = Field(default_factory=datetime.utcnow, description="Error timestamp")
    request_id: Optional[str] = Field(None, description="Request identifier for tracking")


class TransactionResponse(BaseModel):
    """Response model for transaction data."""
    
    id: str = Field(..., description="Transaction identifier")
    account_id: str = Field(..., description="Account identifier")
    amount: Decimal = Field(..., description="Transaction amount")
    transaction_type: str = Field(..., description="Transaction type")
    status: str = Field(..., description="Transaction status")
    description: Optional[str] = Field(None, description="Transaction description")
    created_at: datetime = Field(..., description="Transaction creation timestamp")
    updated_at: Optional[datetime] = Field(None, description="Last update timestamp")
    from_account_id: Optional[str] = Field(None, description="Source account for transfers")
    to_account_id: Optional[str] = Field(None, description="Destination account for transfers")


class TransactionHistoryResponse(BaseModel):
    """Response model for transaction history."""
    
    content: List[TransactionResponse] = Field(..., description="List of transactions")
    total_elements: int = Field(..., description="Total number of transactions")
    total_pages: int = Field(..., description="Total number of pages")
    current_page: int = Field(..., description="Current page number")
    page_size: int = Field(..., description="Page size")
    has_next: bool = Field(..., description="Whether there are more pages")
    has_previous: bool = Field(..., description="Whether there are previous pages")


class TransactionAnalyticsResponse(BaseModel):
    """Response model for transaction analytics."""
    
    account_id: Optional[str] = Field(None, description="Account identifier")
    total_deposits: Decimal = Field(..., description="Total deposits amount")
    total_withdrawals: Decimal = Field(..., description="Total withdrawals amount")
    total_transfers_in: Decimal = Field(..., description="Total incoming transfers")
    total_transfers_out: Decimal = Field(..., description="Total outgoing transfers")
    transaction_count: int = Field(..., description="Total number of transactions")
    average_transaction_amount: Decimal = Field(..., description="Average transaction amount")
    largest_transaction: Decimal = Field(..., description="Largest transaction amount")
    period_start: datetime = Field(..., description="Analytics period start")
    period_end: datetime = Field(..., description="Analytics period end")


class MCPSuccessResponse(BaseModel):
    """Response model for successful MCP operations."""
    
    success: bool = Field(default=True, description="Operation success status")
    message: str = Field(..., description="Success message")
    data: Optional[Dict[str, Any]] = Field(None, description="Response data")
    timestamp: datetime = Field(default_factory=datetime.utcnow, description="Response timestamp")
    request_id: Optional[str] = Field(None, description="Request identifier for tracking")